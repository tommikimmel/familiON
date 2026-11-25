package com.familion.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class SecurityIT extends IntegrationTestBase {

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private TokenRevocationService tokenRevocationService;

    @Test
    void returns401ProblemJsonWhenNoTokenIsProvided() {
        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/api/v1/test/security/me")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .matches(mt -> mt.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asInt()).isEqualTo(401);
        assertThat(response.getBody().path("type").asText())
                .isEqualTo("https://familion.app/problems/unauthorized");
        assertThat(response.getBody().path("instance").asText()).isEqualTo("/api/v1/test/security/me");
    }

    @Test
    void returns200AndCurrentUserWhenValidAccessTokenIsProvided() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String bearer = tokens.bearer(userId, familyId, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);

        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/api/v1/test/security/me")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("userId").asText()).isEqualTo(userId.toString());
        assertThat(response.getBody().path("familyId").asText()).isEqualTo(familyId.toString());
        assertThat(response.getBody().path("role").asText()).isEqualTo("HIJO");
        assertThat(response.getBody().path("visibility").asText()).isEqualTo("FAMILY_SUMMARY");
    }

    @Test
    void returns401WhenTwoFactorChallengeTokenIsUsedAsBearer() {
        String challengeToken =
                accessTokenIssuer.issueChallenge(UUID.randomUUID(), Duration.ofMinutes(5));

        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/api/v1/test/security/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + challengeToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .matches(mt -> mt.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returns401WhenAccessTokenHasBeenRevoked() {
        IssuedAccessToken issued = accessTokenIssuer.issue(
                UUID.randomUUID(), UUID.randomUUID(), Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        tokenRevocationService.revoke(issued.jti(), issued.expiresAt());

        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/api/v1/test/security/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issued.token())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .matches(mt -> mt.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returns403WhenChildCallsAdultOnlyEndpointAnd200WhenAdultCallsIt() {
        UUID familyId = UUID.randomUUID();
        String childBearer =
                tokens.bearer(UUID.randomUUID(), familyId, Role.HIJO, FamilyVisibility.FAMILY_FULL);
        String adultBearer =
                tokens.bearer(UUID.randomUUID(), familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        ResponseEntity<JsonNode> forbidden = client()
                .get()
                .uri("/api/v1/test/security/adult-only")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getHeaders().getContentType())
                .isNotNull()
                .matches(mt -> mt.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(forbidden.getBody()).isNotNull();
        assertThat(forbidden.getBody().path("status").asInt()).isEqualTo(403);
        assertThat(forbidden.getBody().path("type").asText())
                .isEqualTo("https://familion.app/problems/forbidden");

        ResponseEntity<JsonNode> allowed = client()
                .get()
                .uri("/api/v1/test/security/adult-only")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void publicSystemVersionRouteDoesNotRequireToken() {
        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/api/v1/system/version")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void publicAuthLoginRouteDoesNotRequireToken() {
        ResponseEntity<JsonNode> response = client()
                .post()
                .uri("/api/v1/auth/login")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
