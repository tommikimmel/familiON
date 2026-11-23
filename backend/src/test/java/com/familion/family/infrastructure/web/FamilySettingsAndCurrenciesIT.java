package com.familion.family.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.family.application.api.FamilyQueryApi;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

class FamilySettingsAndCurrenciesIT extends IntegrationTestBase {

    @Autowired
    private FamilyQueryApi familyQueryApi;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RestClient patchCapableClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void getFamilyReturnsSettingsAndEnabledCurrenciesWithExactContract() {
        UUID familyId = fixtures.createFamily("Familia Morales " + UUID.randomUUID());
        UUID childId = fixtures.createUser(
                familyId,
                "child-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        String childBearer =
                tokens.bearer(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/api/v1/family")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("id").asText()).isEqualTo(familyId.toString());
        assertThat(body.path("name").asText()).startsWith("Familia Morales");
        assertThat(body.path("baseCurrency").asText()).isEqualTo("ARS");
        assertThat(body.path("referenceCurrency").asText()).isEqualTo("USD");
        assertThat(body.path("referenceRateSource").asText()).isEqualTo("USD_MEP");
        assertThat(body.path("timezone").asText()).isEqualTo("America/Argentina/Buenos_Aires");

        JsonNode enabled = body.path("enabledCurrencies");
        assertThat(enabled.isArray()).isTrue();
        assertThat(enabled).hasSize(2);
        assertThat(enabled.get(0).path("currencyCode").asText()).isEqualTo("ARS");
        assertThat(enabled.get(0).path("preferredRateSource").isNull()).isTrue();
        assertThat(enabled.get(0).has("isActive")).isTrue();
        assertThat(enabled.get(0).path("isActive").asBoolean()).isTrue();
        assertThat(enabled.get(1).path("currencyCode").asText()).isEqualTo("USD");
        assertThat(enabled.get(1).path("preferredRateSource").asText()).isEqualTo("USD_MEP");
        assertThat(enabled.get(1).path("isActive").asBoolean()).isTrue();
    }

    @Test
    void patchSettingsSucceedsForAdultAndReturns403ForChild() {
        UUID familyId = fixtures.createFamily("Familia Diaz " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childId = fixtures.createUser(
                familyId,
                "child-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);

        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childBearer =
                tokens.bearer(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        RestClient patchClient = patchCapableClient();

        ResponseEntity<JsonNode> forbidden = patchClient
                .patch()
                .uri("/api/v1/family/settings")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Hacked Name"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> updated = patchClient
                .patch()
                .uri("/api/v1/family/settings")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Familia Diaz Renovada",
                        "baseCurrency", "USD",
                        "referenceCurrency", "EUR",
                        "referenceRateSource", "EUR_OFICIAL",
                        "timezone", "America/Montevideo"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = updated.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("name").asText()).isEqualTo("Familia Diaz Renovada");
        assertThat(body.path("baseCurrency").asText()).isEqualTo("USD");
        assertThat(body.path("referenceCurrency").asText()).isEqualTo("EUR");
        assertThat(body.path("referenceRateSource").asText()).isEqualTo("EUR_OFICIAL");
        assertThat(body.path("timezone").asText()).isEqualTo("America/Montevideo");
    }

    @Test
    void patchSettingsRejectsBaseCurrencyChangeWhenTransactionsExistWith409() {
        UUID familyId = fixtures.createFamily("Familia Tx " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-tx-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID accountId = fixtures.createAccount(familyId, adultId, "CASH", "ARS");

        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id, family_id, user_id, attributed_to_user_id, account_id,
                    type, scope, status, amount, currency_code, transaction_date, description
                )
                VALUES (?, ?, ?, ?, ?, 'EXPENSE', 'FAMILY', 'POSTED', ?, 'ARS', ?, 'Supermercado')
                """,
                UUID.randomUUID(),
                familyId,
                adultId,
                adultId,
                accountId,
                new BigDecimal("15000.0000"),
                LocalDate.of(2026, 5, 10));

        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        RestClient patchClient = patchCapableClient();

        ResponseEntity<JsonNode> conflict = patchClient
                .patch()
                .uri("/api/v1/family/settings")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("baseCurrency", "USD"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).isNotNull();
        assertThat(conflict.getBody().path("detail").asText())
                .isEqualTo("Base currency cannot change once transactions exist");

        // Changing other fields while keeping baseCurrency unchanged still succeeds
        ResponseEntity<JsonNode> ok = patchClient
                .patch()
                .uri("/api/v1/family/settings")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Familia Tx Renamed", "baseCurrency", "ARS"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().path("name").asText()).isEqualTo("Familia Tx Renamed");
    }

    @Test
    void patchSettingsRejectsInvalidValuesWith400() {
        UUID familyId = fixtures.createFamily("Familia Invalid " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-inv-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        RestClient patchClient = patchCapableClient();

        for (Map<String, Object> invalidPayload : List.of(
                Map.<String, Object>of("baseCurrency", "BTC"),
                Map.<String, Object>of("referenceCurrency", "GBP"),
                Map.<String, Object>of("referenceRateSource", "UNKNOWN_SOURCE"),
                Map.<String, Object>of("timezone", "Invalid/Zone"),
                Map.<String, Object>of("name", "   "))) {
            ResponseEntity<JsonNode> resp = patchClient
                    .patch()
                    .uri("/api/v1/family/settings")
                    .header(HttpHeaders.AUTHORIZATION, adultBearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(invalidPayload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .toEntity(JsonNode.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void enablingEurUpdatesFamilyEndpointAndFamilyQueryApiAndSupportsPatchLifecycle() {
        UUID familyId = fixtures.createFamily("Familia Currencies " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-cur-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        RestClient patchClient = patchCapableClient();

        // Enable EUR -> 201
        ResponseEntity<JsonNode> created = client()
                .post()
                .uri("/api/v1/family/currencies")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currencyCode", "EUR", "preferredRateSource", "EUR_OFICIAL"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().path("currencyCode").asText()).isEqualTo("EUR");
        assertThat(created.getBody().path("preferredRateSource").asText()).isEqualTo("EUR_OFICIAL");
        assertThat(created.getBody().path("isActive").asBoolean()).isTrue();

        // Appears in GET /api/v1/family
        ResponseEntity<JsonNode> familyResp = client()
                .get()
                .uri("/api/v1/family")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        List<String> familyCurrencyCodes = new ArrayList<>();
        familyResp
                .getBody()
                .path("enabledCurrencies")
                .forEach(node -> familyCurrencyCodes.add(node.path("currencyCode").asText()));
        assertThat(familyCurrencyCodes).containsExactly("ARS", "USD", "EUR");

        // Appears in FamilyQueryApi.getSettings().enabledCurrencies()
        assertThat(familyQueryApi.getSettings(familyId).enabledCurrencies())
                .containsExactly("ARS", "USD", "EUR");

        // Enabling EUR again -> 409
        ResponseEntity<JsonNode> duplicate = client()
                .post()
                .uri("/api/v1/family/currencies")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currencyCode", "EUR"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Deactivating baseCurrency (ARS) -> 409
        ResponseEntity<JsonNode> deactivateBase = patchClient
                .patch()
                .uri("/api/v1/family/currencies/ARS")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("isActive", false))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(deactivateBase.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Updating EUR rate source & deactivating -> 200
        ResponseEntity<JsonNode> patchedEur = patchClient
                .patch()
                .uri("/api/v1/family/currencies/EUR")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("isActive", false, "preferredRateSource", "EUR_BLUE"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(patchedEur.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchedEur.getBody().path("isActive").asBoolean()).isFalse();
        assertThat(patchedEur.getBody().path("preferredRateSource").asText()).isEqualTo("EUR_BLUE");

        // EUR still listed in /api/v1/family/currencies with isActive=false, excluded from enabledCurrencies
        ResponseEntity<JsonNode> listResp = client()
                .get()
                .uri("/api/v1/family/currencies")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody()).hasSize(3);
        assertThat(listResp.getBody().get(2).path("currencyCode").asText()).isEqualTo("EUR");
        assertThat(listResp.getBody().get(2).path("isActive").asBoolean()).isFalse();
        assertThat(familyQueryApi.getSettings(familyId).enabledCurrencies())
                .containsExactly("ARS", "USD");
    }
}
