package com.familion.bff.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.familion.bff.core.CoreClientException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/bff/api/accounts");
    }

    @Test
    void coreUnavailableMapsToBadGatewayProblem() {
        ResponseEntity<ProblemDetail> response =
                handler.handleCoreUnavailable(new ResourceAccessException("connection refused"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Core unavailable");
        assertThat(response.getBody().getInstance()).isEqualTo(URI.create("/bff/api/accounts"));
    }

    @Test
    void coreClientExceptionWithBodyIsPropagatedVerbatim() {
        String problemJson = "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404}";

        ResponseEntity<String> response =
                handler.handleCoreClientException(new CoreClientException(404, problemJson), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isEqualTo(problemJson);
    }

    @Test
    void coreClientExceptionWithoutBodyBuildsMinimalProblem() {
        ResponseEntity<String> response =
                handler.handleCoreClientException(new CoreClientException(409, "  "), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody())
                .contains("\"status\":409")
                .contains("\"title\":\"Conflict\"");
    }

    @Test
    void unreadablePayloadMapsToBadRequestProblem() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("bad json", mock(HttpInputMessage.class));

        ResponseEntity<ProblemDetail> response = handler.handleBadRequest(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Bad Request");
        assertThat(response.getBody().getDetail()).isEqualTo("Invalid request payload.");
    }

    @Test
    void unexpectedErrorMapsToGenericInternalServerErrorWithoutLeakingDetails() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new IllegalStateException("secret internal detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getDetail()).doesNotContain("secret internal detail");
    }
}
