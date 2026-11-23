package com.familion;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OpenApiIT extends IntegrationTestBase {

    @Test
    void openApiDocsEndpointReturnsOkWithSpec() {
        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/v3/api-docs")
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("openapi").asText()).startsWith("3.");
    }
}
