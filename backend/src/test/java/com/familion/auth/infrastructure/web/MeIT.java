package com.familion.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
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

class MeIT extends IntegrationTestBase {

    private RestClient patchCapableClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void meReturnsAuthenticatedUserProfileAndFamilyWithEnabledCurrencies() {
        String email = "me-" + UUID.randomUUID() + "@familion.test";
        ResponseEntity<JsonNode> reg = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "ProfilePass123",
                        "fullName", "Sofia Martinez",
                        "familyName", "Familia Martinez"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String userId = reg.getBody().path("userId").asText();

        ResponseEntity<JsonNode> login = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "ProfilePass123"))
                .retrieve()
                .toEntity(JsonNode.class);
        String accessToken = login.getBody().path("accessToken").asText();

        ResponseEntity<JsonNode> meResponse = client()
                .get()
                .uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = meResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("id").asText()).isEqualTo(userId);
        assertThat(body.path("email").asText()).isEqualTo(email);
        assertThat(body.path("fullName").asText()).isEqualTo("Sofia Martinez");
        assertThat(body.path("role").asText()).isEqualTo("ADULTO");
        assertThat(body.path("familyVisibility").asText()).isEqualTo("FAMILY_FULL");
        assertThat(body.path("themePreference").asText()).isEqualTo("SYSTEM");
        assertThat(body.path("accentColor").asText()).isEqualTo("#0F766E");
        assertThat(body.path("twoFactorEnabled").asBoolean()).isTrue();
        assertThat(body.path("emailVerified").asBoolean()).isFalse();

        JsonNode family = body.path("family");
        assertThat(family.path("id").asText()).isNotBlank();
        assertThat(family.path("name").asText()).isEqualTo("Familia Martinez");
        assertThat(family.path("baseCurrency").asText()).isEqualTo("ARS");
        assertThat(family.path("referenceCurrency").asText()).isEqualTo("USD");
        assertThat(family.path("referenceRateSource").asText()).isEqualTo("USD_MEP");
        assertThat(family.path("timezone").asText()).isEqualTo("America/Argentina/Buenos_Aires");
        List<String> currencies = new ArrayList<>();
        family.path("enabledCurrencies").forEach(node -> currencies.add(node.asText()));
        assertThat(currencies).containsExactly("ARS", "USD");
    }

    @Test
    void patchPreferencesUpdatesAndValidatesThemeAndAccentColor() {
        String email = "prefs-" + UUID.randomUUID() + "@familion.test";
        client().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "PrefsPass123",
                        "fullName", "Diego Lopez",
                        "familyName", "Familia Lopez"))
                .retrieve()
                .toEntity(JsonNode.class);

        String accessToken = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "PrefsPass123"))
                .retrieve()
                .toEntity(JsonNode.class)
                .getBody()
                .path("accessToken")
                .asText();

        RestClient patchClient = patchCapableClient();

        ResponseEntity<JsonNode> updated = patchClient
                .patch()
                .uri("/api/v1/auth/me/preferences")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("themePreference", "DARK", "accentColor", "#2563EB"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().path("themePreference").asText()).isEqualTo("DARK");
        assertThat(updated.getBody().path("accentColor").asText()).isEqualTo("#2563EB");

        // Partial update preserves previously updated fields
        ResponseEntity<JsonNode> partial = patchClient
                .patch()
                .uri("/api/v1/auth/me/preferences")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("themePreference", "LIGHT"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(partial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(partial.getBody()).isNotNull();
        assertThat(partial.getBody().path("themePreference").asText()).isEqualTo("LIGHT");
        assertThat(partial.getBody().path("accentColor").asText()).isEqualTo("#2563EB");

        // Invalid accent color -> 400
        ResponseEntity<JsonNode> invalidColor = patchClient
                .patch()
                .uri("/api/v1/auth/me/preferences")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("accentColor", "not-hex"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(invalidColor.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Invalid theme enum -> 400
        ResponseEntity<JsonNode> invalidTheme = patchClient
                .patch()
                .uri("/api/v1/auth/me/preferences")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("themePreference", "NEON"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(invalidTheme.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void meRequiresAuthentication() {
        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/api/v1/auth/me")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
