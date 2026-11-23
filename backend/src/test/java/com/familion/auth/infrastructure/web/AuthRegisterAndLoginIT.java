package com.familion.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.auth.application.api.UserRegisteredEvent;
import com.familion.auth.domain.RefreshTokenCrypto;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthRegisterAndLoginIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CapturedRegistrationEvents capturedEvents;

    @BeforeEach
    void clearEvents() {
        capturedEvents.clear();
    }

    @Test
    void registerCreatesFamilyCurrenciesAdultUserAndPublishesEvent() {
        String unique = UUID.randomUUID().toString();
        String rawEmail = "  Ana." + unique + "@Familion.TEST ";

        ResponseEntity<JsonNode> response = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", rawEmail,
                        "password", "SecurePass123",
                        "fullName", "Ana Gomez",
                        "familyName", "Familia Gomez"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        UUID userId = UUID.fromString(response.getBody().path("userId").asText());
        String normalizedEmail = rawEmail.trim().toLowerCase();
        assertThat(response.getBody().path("email").asText()).isEqualTo(normalizedEmail);

        UUID familyId = jdbcTemplate.queryForObject(
                "SELECT family_id FROM users WHERE id = ?", UUID.class, userId);
        List<String> currencies = jdbcTemplate.queryForList(
                "SELECT currency_code FROM family_currencies WHERE family_id = ? ORDER BY currency_code",
                String.class,
                familyId);
        assertThat(currencies).containsExactly("ARS", "USD");

        String role = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, userId);
        assertThat(role).isEqualTo("ADULTO");

        assertThat(capturedEvents.events())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.userId()).isEqualTo(userId);
                    assertThat(event.familyId()).isEqualTo(familyId);
                    assertThat(event.email()).isEqualTo(normalizedEmail);
                    assertThat(event.fullName()).isEqualTo("Ana Gomez");
                });
    }

    @Test
    void registerRejectsDuplicateEmailWith409AndWeakPasswordWith400() {
        String email = "dup-" + UUID.randomUUID() + "@familion.test";
        ResponseEntity<JsonNode> first = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "StrongPass1",
                        "fullName", "Carlos",
                        "familyName", "Familia Carlos"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> duplicate = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email.toUpperCase(),
                        "password", "StrongPass1",
                        "fullName", "Carlos 2",
                        "familyName", "Otra Familia"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<JsonNode> weakShort = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", "weak1-" + UUID.randomUUID() + "@familion.test",
                        "password", "Short1",
                        "fullName", "Weak",
                        "familyName", "Familia"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(weakShort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> weakNoDigit = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", "weak2-" + UUID.randomUUID() + "@familion.test",
                        "password", "OnlyLettersPassword",
                        "fullName", "Weak",
                        "familyName", "Familia"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(weakNoDigit.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void loginReturnsValidTokensAndStoresOnlyHashedRefreshToken() {
        String email = "login-" + UUID.randomUUID() + "@familion.test";
        registerUser(email, "LoginPass123");
        jdbcTemplate.update("UPDATE users SET two_factor_enabled = false WHERE email = ?", email);

        String longUserAgent = "Mozilla/5.0 " + "x".repeat(300);
        ResponseEntity<JsonNode> loginResponse = client()
                .post()
                .uri("/api/v1/auth/login")
                .header(HttpHeaders.USER_AGENT, longUserAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "LoginPass123"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = loginResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("requires2fa").asBoolean()).isFalse();
        assertThat(body.path("challengeToken").isNull()).isTrue();
        assertThat(body.path("deviceToken").isNull()).isTrue();
        String accessToken = body.path("accessToken").asText();
        String refreshToken = body.path("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
        assertThat(body.path("accessTokenExpiresIn").asLong()).isPositive();
        assertThat(body.path("refreshTokenExpiresIn").asLong()).isPositive();

        // Refresh token is never stored in plain text in the database
        Integer plainCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?",
                Integer.class,
                refreshToken);
        assertThat(plainCount).isZero();

        String expectedHash = RefreshTokenCrypto.sha256Hex(refreshToken);
        String storedDeviceInfo = jdbcTemplate.queryForObject(
                "SELECT device_info FROM refresh_tokens WHERE token_hash = ?",
                String.class,
                expectedHash);
        assertThat(storedDeviceInfo).isNotNull().hasSize(255);
    }

    @Test
    void loginReturnsGeneric401AndLocksOutOnSixthFailedAttemptWithRetryAfter() {
        String email = "lockout-" + UUID.randomUUID() + "@familion.test";
        registerUser(email, "RightPass123");

        for (int attempt = 1; attempt <= 5; attempt++) {
            ResponseEntity<JsonNode> failed = client()
                    .post()
                    .uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", "WrongPass123"))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .toEntity(JsonNode.class);

            assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(failed.getHeaders().getContentType())
                    .isNotNull()
                    .matches(mt -> mt.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            assertThat(failed.getBody()).isNotNull();
            assertThat(failed.getBody().path("detail").asText()).isEqualTo("Invalid credentials");
        }

        ResponseEntity<JsonNode> locked = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "WrongPass123"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        String retryAfter = locked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
    }

    private void registerUser(String email, String password) {
        ResponseEntity<JsonNode> response = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", password,
                        "fullName", "Test User",
                        "familyName", "Familia Test"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @TestConfiguration
    static class EventCaptureConfig {

        @Bean
        CapturedRegistrationEvents capturedRegistrationEvents() {
            return new CapturedRegistrationEvents();
        }
    }

    static class CapturedRegistrationEvents {

        private final List<UserRegisteredEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void onUserRegistered(UserRegisteredEvent event) {
            events.add(event);
        }

        List<UserRegisteredEvent> events() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }
}
