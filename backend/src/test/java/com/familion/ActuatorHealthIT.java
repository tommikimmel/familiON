package com.familion;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ActuatorHealthIT extends IntegrationTestBase {

    @Test
    void healthEndpointReturnsUp() {
        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/actuator/health")
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
    }

    @Test
    void livenessProbeEndpointReturnsUp() {
        ResponseEntity<JsonNode> response = client()
                .get()
                .uri("/actuator/health/liveness")
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
    }
}
