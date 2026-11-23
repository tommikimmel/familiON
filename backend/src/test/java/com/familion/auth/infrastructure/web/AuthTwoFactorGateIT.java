package com.familion.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.auth.application.api.SecondFactorGate;
import com.familion.common.security.AccessTokenIssuer;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class AuthTwoFactorGateIT extends IntegrationTestBase {

    @Test
    void loginReturnsRequires2faAndNoSessionTokensWhenSecondFactorGateChallenges() {
        String email = "2fa-" + UUID.randomUUID() + "@familion.test";
        ResponseEntity<JsonNode> registered = client()
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", "SecurePass123",
                        "fullName", "Two Factor User",
                        "familyName", "Familia 2FA"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> loginResponse = client()
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", "SecurePass123"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = loginResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("requires2fa").asBoolean()).isTrue();
        assertThat(body.path("challengeToken").asText()).isNotBlank();
        assertThat(body.path("accessToken").isNull()).isTrue();
        assertThat(body.path("accessTokenExpiresIn").isNull()).isTrue();
        assertThat(body.path("refreshToken").isNull()).isTrue();
        assertThat(body.path("refreshTokenExpiresIn").isNull()).isTrue();
        assertThat(body.path("deviceToken").isNull()).isTrue();
    }

    @TestConfiguration
    static class StubSecondFactorGateConfig {

        @Bean
        @Primary
        SecondFactorGate stubSecondFactorGate(AccessTokenIssuer accessTokenIssuer) {
            return (user, deviceToken) ->
                    Optional.of(accessTokenIssuer.issueChallenge(user.id(), Duration.ofMinutes(5)));
        }
    }
}
