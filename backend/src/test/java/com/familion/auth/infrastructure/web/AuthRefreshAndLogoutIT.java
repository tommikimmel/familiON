package com.familion.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.auth.domain.RefreshTokenCrypto;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthRefreshAndLogoutIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void refreshRotatesTokensAndReuseRevokesEntireTokenFamily() {
        String email = "rotate-" + UUID.randomUUID() + "@familion.test";
        register(email, "RotatePass123");

        JsonNode sessionA = login(email, "RotatePass123");
        JsonNode sessionB = login(email, "RotatePass123");
        String rt1 = sessionA.path("refreshToken").asText();
        String rtOtherDevice = sessionB.path("refreshToken").asText();

        ResponseEntity<JsonNode> rotatedResponse = client()
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", rt1))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(rotatedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode rotated = rotatedResponse.getBody();
        assertThat(rotated).isNotNull();
        String rt2 = rotated.path("refreshToken").asText();
        assertThat(rt2).isNotBlank().isNotEqualTo(rt1);
        assertThat(rotated.path("accessToken").asText()).isNotBlank();

        // Reuse the already-rotated rt1 -> 401 and revokes ALL tokens for the user
        ResponseEntity<JsonNode> reuseResponse = client()
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", rt1))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(reuseResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // rt2 (newly rotated token) must now be rejected because the whole family was revoked
        ResponseEntity<JsonNode> rt2AfterReuse = client()
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", rt2))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(rt2AfterReuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Other device's refresh token for the same user must also be revoked
        ResponseEntity<JsonNode> otherAfterReuse = client()
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", rtOtherDevice))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(otherAfterReuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshReturns401ForUnknownOrExpiredToken() {
        ResponseEntity<JsonNode> unknown = client()
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", "non-existent-refresh-token"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String email = "expired-" + UUID.randomUUID() + "@familion.test";
        register(email, "ExpirePass123");
        JsonNode session = login(email, "ExpirePass123");
        String rt = session.path("refreshToken").asText();
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET expires_at = NOW() - INTERVAL '1 minute' WHERE token_hash = ?",
                RefreshTokenCrypto.sha256Hex(rt));

        ResponseEntity<JsonNode> expired = client()
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", rt))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesAccessTokenAndRefreshToken() {
        String email = "logout-" + UUID.randomUUID() + "@familion.test";
        register(email, "LogoutPass123");
        JsonNode session = login(email, "LogoutPass123");
        String accessToken = session.path("accessToken").asText();
        String refreshToken = session.path("refreshToken").asText();

        ResponseEntity<Void> logoutResponse = client()
                .post()
                .uri("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken))
                .retrieve()
                .toBodilessEntity();
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Previous access token is immediately rejected with 401
        ResponseEntity<JsonNode> protectedCall = client()
                .get()
                .uri("/api/v1/test/security/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(protectedCall.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Revoked refresh token is rejected with 401
        ResponseEntity<JsonNode> refreshCall = client()
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(refreshCall.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void register(String email, String password) {
        ResponseEntity<JsonNode> response = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", password,
                        "fullName", "Session User",
                        "familyName", "Familia Session"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbcTemplate.update("UPDATE users SET two_factor_enabled = false WHERE email = ?", email);
    }

    private JsonNode login(String email, String password) {
        ResponseEntity<JsonNode> response = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
