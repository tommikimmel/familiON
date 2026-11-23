package com.familion.family.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class FamilyMembersIT extends IntegrationTestBase {

    private RestClient patchCapableClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void listMembersReturnsOnlyCurrentFamilyMembersWithExactContract() {
        UUID familyA = fixtures.createFamily("Family A " + UUID.randomUUID());
        UUID familyB = fixtures.createFamily("Family B " + UUID.randomUUID());

        String adultEmail = "adult-a-" + UUID.randomUUID() + "@familion.test";
        String childEmail = "child-a-" + UUID.randomUUID() + "@familion.test";
        UUID adultA = fixtures.createUser(familyA, adultEmail, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        UUID childA = fixtures.createUser(familyA, childEmail, Role.HIJO, FamilyVisibility.OWN_ONLY);
        fixtures.createUser(
                familyB,
                "other-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);

        String childBearer =
                tokens.bearer(childA, familyA, Role.HIJO, FamilyVisibility.OWN_ONLY);

        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/api/v1/family/members")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body).hasSize(2);

        JsonNode first = body.get(0);
        assertThat(first.path("id").asText()).isEqualTo(adultA.toString());
        assertThat(first.path("fullName").asText()).isNotBlank();
        assertThat(first.path("email").asText()).isEqualTo(adultEmail);
        assertThat(first.path("role").asText()).isEqualTo("ADULTO");
        assertThat(first.path("familyVisibility").asText()).isEqualTo("FAMILY_FULL");
        assertThat(first.path("accentColor").asText()).isEqualTo("#0F766E");
        assertThat(first.has("isActive")).isTrue();
        assertThat(first.path("isActive").asBoolean()).isTrue();

        JsonNode second = body.get(1);
        assertThat(second.path("id").asText()).isEqualTo(childA.toString());
        assertThat(second.path("email").asText()).isEqualTo(childEmail);
        assertThat(second.path("role").asText()).isEqualTo("HIJO");
        assertThat(second.path("familyVisibility").asText()).isEqualTo("OWN_ONLY");
    }

    @Test
    void patchVisibilityUpdatesChildRejectsAdultWith409AndOtherFamilyWith404() {
        UUID familyA = fixtures.createFamily("Family Vis A " + UUID.randomUUID());
        UUID familyB = fixtures.createFamily("Family Vis B " + UUID.randomUUID());

        UUID adultA = fixtures.createUser(
                familyA,
                "adult-vis-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childA = fixtures.createUser(
                familyA,
                "child-vis-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        UUID childB = fixtures.createUser(
                familyB,
                "child-other-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);

        String adultBearer =
                tokens.bearer(adultA, familyA, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childBearer =
                tokens.bearer(childA, familyA, Role.HIJO, FamilyVisibility.OWN_ONLY);
        RestClient patchClient = patchCapableClient();

        // HIJO caller -> 403
        ResponseEntity<JsonNode> forbidden = patchClient
                .patch()
                .uri("/api/v1/family/members/{userId}/visibility", childA)
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("familyVisibility", "FAMILY_FULL"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ADULTO updating HIJO -> 200
        ResponseEntity<JsonNode> updated = patchClient
                .patch()
                .uri("/api/v1/family/members/{userId}/visibility", childA)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("familyVisibility", "FAMILY_SUMMARY"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().path("id").asText()).isEqualTo(childA.toString());
        assertThat(updated.getBody().path("familyVisibility").asText()).isEqualTo("FAMILY_SUMMARY");
        assertThat(updated.getBody().path("isActive").asBoolean()).isTrue();

        // ADULTO updating ADULTO -> 409
        ResponseEntity<JsonNode> adultConflict = patchClient
                .patch()
                .uri("/api/v1/family/members/{userId}/visibility", adultA)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("familyVisibility", "OWN_ONLY"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(adultConflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // ADULTO updating member of another family -> 404
        ResponseEntity<JsonNode> otherFamilyNotFound = patchClient
                .patch()
                .uri("/api/v1/family/members/{userId}/visibility", childB)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("familyVisibility", "FAMILY_FULL"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(otherFamilyNotFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Invalid visibility -> 400
        ResponseEntity<JsonNode> invalid = patchClient
                .patch()
                .uri("/api/v1/family/members/{userId}/visibility", childA)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("familyVisibility", "UNKNOWN"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
