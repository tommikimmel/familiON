package com.familion.finance.categories.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.cache.CacheKeys;
import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.categories.application.api.CategoryRef;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class CategoryControllerIT extends IntegrationTestBase {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CategoryQueryApi categoryQueryApi;

    private RestClient patchCapableClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void getCategoriesReturnsSeededSystemCategoriesFiltersByKindAndCachesInRedis() {
        UUID familyId = fixtures.createFamily("Familia Cat " + UUID.randomUUID());
        UUID childId = fixtures.createUser(
                familyId,
                "child-cat-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        String childBearer =
                tokens.bearer(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        String cacheKey = CacheKeys.categories(familyId);
        redisTemplate.delete(cacheKey);

        ResponseEntity<JsonNode> allResp = client()
                .get()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(allResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allResp.getBody()).isNotNull();
        assertThat(allResp.getBody()).hasSize(31);

        // Verify contract fields on VEHICLES_FUEL
        JsonNode vehicles = findBySystemKey(allResp.getBody(), "VEHICLES");
        JsonNode fuel = findBySystemKey(allResp.getBody(), "VEHICLES_FUEL");
        assertThat(vehicles).isNotNull();
        assertThat(fuel).isNotNull();
        assertThat(fuel.path("parentCategoryId").asText()).isEqualTo(vehicles.path("id").asText());
        assertThat(fuel.path("name").asText()).isEqualTo("Combustible");
        assertThat(fuel.path("kind").asText()).isEqualTo("EXPENSE");
        assertThat(fuel.path("icon").asText()).isEqualTo("fuel");
        assertThat(fuel.path("isSystem").asBoolean()).isTrue();
        assertThat(fuel.path("systemKey").asText()).isEqualTo("VEHICLES_FUEL");

        // Verify Redis cache populated with ~1h TTL
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
        Long ttlSeconds = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isNotNull().isGreaterThan(3500L).isLessThanOrEqualTo(3600L);

        // Filter by kind
        ResponseEntity<JsonNode> incomeResp = client()
                .get()
                .uri("/api/v1/categories?kind=INCOME")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(incomeResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(incomeResp.getBody()).hasSize(7);
        incomeResp
                .getBody()
                .forEach(node -> assertThat(node.path("kind").asText()).isEqualTo("INCOME"));

        ResponseEntity<JsonNode> expenseResp = client()
                .get()
                .uri("/api/v1/categories?kind=EXPENSE")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(expenseResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(expenseResp.getBody()).hasSize(24);
    }

    @Test
    void createAndEditCategoriesEnforceHierarchyDepthSystemProtectionAndInvalidateCache() {
        UUID familyId = fixtures.createFamily("Familia Cat CRUD " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-cat-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childId = fixtures.createUser(
                familyId,
                "child-cat2-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childBearer =
                tokens.bearer(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        RestClient patchClient = patchCapableClient();
        String cacheKey = CacheKeys.categories(familyId);

        // Prime Redis cache via GET
        client().get()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        // HIJO cannot create category -> 403
        ResponseEntity<JsonNode> childCreate = client()
                .post()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Juegos", "kind", "EXPENSE"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ADULTO creates top-level custom category -> 201 and cache invalidated
        ResponseEntity<JsonNode> createdParent = client()
                .post()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Deportes",
                        "kind", "EXPENSE",
                        "icon", "dumbbell",
                        "colorHex", "#10B981"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(createdParent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID parentId = UUID.fromString(createdParent.getBody().path("id").asText());
        assertThat(createdParent.getBody().path("isSystem").asBoolean()).isFalse();
        assertThat(createdParent.getBody().path("systemKey").isNull()).isTrue();
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        // ADULTO creates child category (depth 2) -> 201
        ResponseEntity<JsonNode> createdChild = client()
                .post()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Tenis",
                        "kind", "EXPENSE",
                        "parentCategoryId", parentId.toString()))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(createdChild.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID childCatId = UUID.fromString(createdChild.getBody().path("id").asText());
        assertThat(createdChild.getBody().path("parentCategoryId").asText())
                .isEqualTo(parentId.toString());

        // Child of a child (depth 3) -> 422
        ResponseEntity<JsonNode> depthThree = client()
                .post()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Pelotas",
                        "kind", "EXPENSE",
                        "parentCategoryId", childCatId.toString()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(depthThree.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Child with mismatched kind -> 422
        ResponseEntity<JsonNode> wrongKind = client()
                .post()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Premio",
                        "kind", "INCOME",
                        "parentCategoryId", parentId.toString()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(wrongKind.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Editing a system category -> 403
        CategoryRef fuelRef = categoryQueryApi.requireSystemCategory("VEHICLES_FUEL");
        ResponseEntity<JsonNode> editSystem = patchClient
                .patch()
                .uri("/api/v1/categories/" + fuelRef.id())
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Nafta"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(editSystem.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Prime cache again, verify custom categories appear, then PATCH and check invalidation
        ResponseEntity<JsonNode> refreshed = client()
                .get()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(refreshed.getBody()).hasSize(33);
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        ResponseEntity<JsonNode> patched = patchClient
                .patch()
                .uri("/api/v1/categories/" + childCatId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Pádel", "colorHex", "#2563EB", "isActive", false))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().path("name").asText()).isEqualTo("Pádel");
        assertThat(patched.getBody().path("colorHex").asText()).isEqualTo("#2563EB");
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        // Deactivated category no longer returned by GET and rejected by CategoryQueryApi
        ResponseEntity<JsonNode> afterDeactivate = client()
                .get()
                .uri("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(afterDeactivate.getBody()).hasSize(32);

        List<String> names = new ArrayList<>();
        afterDeactivate.getBody().forEach(n -> names.add(n.path("name").asText()));
        assertThat(names).contains("Deportes").doesNotContain("Pádel");

        // CategoryQueryApi checks
        assertThat(categoryQueryApi.requireUsable(familyId, parentId, "EXPENSE").name())
                .isEqualTo("Deportes");
        assertThat(categoryQueryApi.requireUsable(familyId, fuelRef.id(), "EXPENSE").name())
                .isEqualTo("Combustible");
        assertThatThrownBy(() -> categoryQueryApi.requireUsable(familyId, childCatId, "EXPENSE"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> categoryQueryApi.requireUsable(familyId, parentId, "INCOME"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> categoryQueryApi.requireSystemCategory("NON_EXISTENT_KEY"))
                .isInstanceOf(NotFoundException.class);
    }

    private static JsonNode findBySystemKey(JsonNode array, String systemKey) {
        for (JsonNode node : array) {
            if (systemKey.equals(node.path("systemKey").asText(null))) {
                return node;
            }
        }
        return null;
    }
}
