package com.familion.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerIT extends IntegrationTestBase {

    private String authHeader() {
        return tokens.bearer(
                UUID.randomUUID(), UUID.randomUUID(), Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    }

    @Test
    void mapsNotFoundExceptionTo404ProblemDetail() {
        ResponseEntity<JsonNode> response = get("/api/v1/test/errors/not-found");

        assertProblem(response, HttpStatus.NOT_FOUND, "not-found", "/api/v1/test/errors/not-found");
        assertThat(response.getBody().path("detail").asText()).isEqualTo("Account not found");
    }

    @Test
    void mapsForbiddenExceptionTo403ProblemDetail() {
        ResponseEntity<JsonNode> response = get("/api/v1/test/errors/forbidden");

        assertProblem(response, HttpStatus.FORBIDDEN, "forbidden", "/api/v1/test/errors/forbidden");
        assertThat(response.getBody().path("detail").asText())
                .isEqualTo("Family view is not permitted");
    }

    @Test
    void mapsConflictExceptionTo409ProblemDetail() {
        ResponseEntity<JsonNode> response = get("/api/v1/test/errors/conflict");

        assertProblem(response, HttpStatus.CONFLICT, "conflict", "/api/v1/test/errors/conflict");
        assertThat(response.getBody().path("detail").asText()).isEqualTo("Email already registered");
    }

    @Test
    void mapsBusinessRuleExceptionTo422ProblemDetail() {
        ResponseEntity<JsonNode> response = get("/api/v1/test/errors/business-rule");

        assertProblem(
                response,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "/api/v1/test/errors/business-rule");
        assertThat(response.getBody().path("detail").asText()).isEqualTo("Insufficient balance");
    }

    @Test
    void mapsTooManyRequestsExceptionTo429WithRetryAfterHeader() {
        ResponseEntity<JsonNode> response = get("/api/v1/test/errors/too-many-requests");

        assertProblem(
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "too-many-requests",
                "/api/v1/test/errors/too-many-requests");
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("45");
    }

    @Test
    void mapsMethodArgumentNotValidTo400WithFieldErrors() {
        ResponseEntity<JsonNode> response = client()
                .post()
                .uri("/api/v1/test/errors/validation")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"\",\"quantity\":0}")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertProblem(
                response, HttpStatus.BAD_REQUEST, "validation-error", "/api/v1/test/errors/validation");
        JsonNode errors = response.getBody().path("errors");
        assertThat(errors.isArray()).isTrue();
        assertThat(errors).hasSize(2);
        assertThat(errors).extracting(node -> node.path("field").asText())
                .containsExactlyInAnyOrder("name", "quantity");
        assertThat(errors).allSatisfy(node -> assertThat(node.path("message").asText()).isNotBlank());
    }

    @Test
    void mapsConstraintViolationTo400WithErrors() {
        ResponseEntity<JsonNode> response = get("/api/v1/test/errors/constraint?limit=2");

        assertProblem(
                response, HttpStatus.BAD_REQUEST, "validation-error", "/api/v1/test/errors/constraint");
        JsonNode errors = response.getBody().path("errors");
        assertThat(errors.isArray()).isTrue();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path("field").asText()).isEqualTo("limit");
        assertThat(errors.get(0).path("message").asText()).isNotBlank();
    }

    @Test
    void mapsMalformedJsonTo400WithErrorsAndNoInternalLeak() {
        ResponseEntity<JsonNode> response = client()
                .post()
                .uri("/api/v1/test/errors/validation")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{invalid-json")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertProblem(
                response, HttpStatus.BAD_REQUEST, "validation-error", "/api/v1/test/errors/validation");
        JsonNode errors = response.getBody().path("errors");
        assertThat(errors.isArray()).isTrue();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).path("field").asText()).isEqualTo("body");
        assertThat(response.getBody().toString()).doesNotContain("JsonParseException");
    }

    @Test
    void mapsDataIntegrityViolationToGeneric409WithoutSqlDetails() {
        ResponseEntity<JsonNode> response = get("/api/v1/test/errors/data-integrity");

        assertProblem(
                response, HttpStatus.CONFLICT, "conflict", "/api/v1/test/errors/data-integrity");
        String body = response.getBody().toString();
        assertThat(body).doesNotContain("uq_users_email");
        assertThat(body).doesNotContain("secret@example.com");
        assertThat(body).doesNotContain("duplicate key");
    }

    @Test
    void mapsUnexpectedExceptionToGeneric500WithoutStackTraceOrDetails() {
        ResponseEntity<JsonNode> response = get("/api/v1/test/errors/unexpected");

        assertProblem(
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "/api/v1/test/errors/unexpected");
        String body = response.getBody().toString();
        assertThat(body).doesNotContain("Sensitive internal database failure details");
        assertThat(body).doesNotContain("IllegalStateException");
        assertThat(body).doesNotContain("at com.familion");
    }

    private ResponseEntity<JsonNode> get(String uri) {
        return client()
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
    }

    private static void assertProblem(
            ResponseEntity<JsonNode> response,
            HttpStatus expectedStatus,
            String expectedSlug,
            String expectedInstance) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .matches(mt -> mt.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asInt()).isEqualTo(expectedStatus.value());
        assertThat(response.getBody().path("type").asText())
                .isEqualTo("https://familion.app/problems/" + expectedSlug);
        assertThat(response.getBody().path("instance").asText()).isEqualTo(expectedInstance);
    }
}
