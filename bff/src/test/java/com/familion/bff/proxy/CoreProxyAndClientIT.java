package com.familion.bff.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.bff.core.CoreClient;
import com.familion.bff.core.CoreClientException;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoreProxyAndClientIT {

    @RegisterExtension
    static WireMockExtension coreMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("familion.core-url", coreMock::baseUrl);
        registry.add("familion.cookie-secure", () -> "false");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private CoreClient coreClient;

    private RestClient client;

    @BeforeEach
    void setUp() {
        coreMock.resetAll();
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Test
    void proxyForwardsGetQueryBearerAndHeadersWithoutBrowserCookies() {
        coreMock.stubFor(get(urlEqualTo("/api/v1/accounts?view=FAMILY"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Content-Disposition", "inline; filename=\"accounts.json\"")
                        .withHeader("Retry-After", "30")
                        .withBody("[{\"id\":\"acc-1\",\"name\":\"Cash\"}]")));

        ProxyResponse response = client.get()
                .uri("/bff/api/accounts?view=FAMILY")
                .header(HttpHeaders.COOKIE, "FAMILION_AT=jwt-proxy-token; OTHER=secret")
                .header("X-Forwarded-For", "203.0.113.10")
                .exchange((req, res) -> new ProxyResponse(
                        res.getStatusCode().value(),
                        res.getHeaders(),
                        res.getBody().readAllBytes()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .isEqualTo("[{\"id\":\"acc-1\",\"name\":\"Cash\"}]");
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/json");
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("inline; filename=\"accounts.json\"");
        assertThat(response.headers().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");

        coreMock.verify(getRequestedFor(urlEqualTo("/api/v1/accounts?view=FAMILY"))
                .withHeader("Authorization", equalTo("Bearer jwt-proxy-token"))
                .withHeader("X-Forwarded-For", containing("203.0.113.10"))
                .withHeader("Cookie", absent()));
    }

    @Test
    void proxyForwardsRequestWithoutCookieAndPropagatesCore401() {
        coreMock.stubFor(get(urlEqualTo("/api/v1/accounts"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Unauthorized\",\"status\":401}")));

        ProxyResponse response = client.get()
                .uri("/bff/api/accounts")
                .exchange((req, res) -> new ProxyResponse(
                        res.getStatusCode().value(),
                        res.getHeaders(),
                        res.getBody().readAllBytes()));

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .isEqualTo("{\"title\":\"Unauthorized\",\"status\":401}");

        coreMock.verify(getRequestedFor(urlEqualTo("/api/v1/accounts"))
                .withHeader("Authorization", absent())
                .withHeader("Cookie", absent()));
    }

    @Test
    void proxyForwardsMultipartFormDataIntact() throws IOException {
        String csrfToken = fetchCsrfToken();
        String boundary = "----FamilionBoundary98765";
        byte[] binaryPngHeader = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Disposition: form-data; name=\"note\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write("Supermarket receipt\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Disposition: form-data; name=\"file\"; filename=\"ticket.png\"\r\n".getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(binaryPngHeader);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] multipartBody = out.toByteArray();

        coreMock.stubFor(post(urlEqualTo("/api/v1/receipts"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"rcpt-1\"}")));

        ProxyResponse response = client.post()
                .uri("/bff/api/receipts")
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken + "; FAMILION_AT=at-upload")
                .header("X-XSRF-TOKEN", csrfToken)
                .body(multipartBody)
                .exchange((req, res) -> new ProxyResponse(
                        res.getStatusCode().value(),
                        res.getHeaders(),
                        res.getBody().readAllBytes()));

        assertThat(response.status()).isEqualTo(201);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":\"rcpt-1\"}");

        coreMock.verify(postRequestedFor(urlEqualTo("/api/v1/receipts"))
                .withHeader("Authorization", equalTo("Bearer at-upload"))
                .withHeader("Content-Type", equalTo("multipart/form-data; boundary=" + boundary))
                .withHeader("Cookie", absent())
                .withRequestBody(binaryEqualTo(multipartBody)));
    }

    @Test
    void proxySupportsPatchMethod() {
        String csrfToken = fetchCsrfToken();
        coreMock.stubFor(patch(urlEqualTo("/api/v1/accounts/acc-1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"acc-1\",\"name\":\"Updated\"}")));

        ProxyResponse response = client.patch()
                .uri("/bff/api/accounts/acc-1")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + csrfToken + "; FAMILION_AT=at-patch")
                .header("X-XSRF-TOKEN", csrfToken)
                .body("{\"name\":\"Updated\"}")
                .exchange((req, res) -> new ProxyResponse(
                        res.getStatusCode().value(),
                        res.getHeaders(),
                        res.getBody().readAllBytes()));

        assertThat(response.status()).isEqualTo(200);
        coreMock.verify(patchRequestedFor(urlEqualTo("/api/v1/accounts/acc-1"))
                .withHeader("Authorization", equalTo("Bearer at-patch")));
    }

    @Test
    void proxyReturns502ProblemJsonWhenCoreIsUnavailable() {
        coreMock.stubFor(get(urlEqualTo("/api/v1/accounts"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        ProxyResponse response = client.get()
                .uri("/bff/api/accounts")
                .exchange((req, res) -> new ProxyResponse(
                        res.getStatusCode().value(),
                        res.getHeaders(),
                        res.getBody().readAllBytes()));

        assertThat(response.status()).isEqualTo(502);
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("\"title\":\"Core unavailable\"");
    }

    @Test
    void coreClientGetsTypedResponsesAndThrowsCoreClientExceptionOnError() {
        coreMock.stubFor(get(urlEqualTo("/api/v1/accounts?view=PERSONAL"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"a1\"},{\"id\":\"a2\"}]")));

        List<Map<String, String>> list = coreClient.get(
                "/accounts?view=PERSONAL",
                "token-abc",
                new ParameterizedTypeReference<>() {});
        assertThat(list).hasSize(2);
        assertThat(list.get(0)).containsEntry("id", "a1");

        coreMock.stubFor(get(urlEqualTo("/api/v1/summary"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"total\":100}")));

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = coreClient.get("/summary", "token-abc", Map.class);
        assertThat(summary).containsEntry("total", 100);

        coreMock.stubFor(get(urlEqualTo("/api/v1/protected"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Unauthorized\",\"status\":401}")));

        assertThatThrownBy(() -> coreClient.get("/protected", "expired", String.class))
                .isInstanceOf(CoreClientException.class)
                .satisfies(ex -> {
                    CoreClientException coreEx = (CoreClientException) ex;
                    assertThat(coreEx.status()).isEqualTo(401);
                    assertThat(coreEx.problemJson()).contains("Unauthorized");
                });
    }

    private String fetchCsrfToken() {
        List<String> cookies = client.get()
                .uri("/bff/csrf")
                .exchange((req, res) -> res.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE));
        String header = cookies.stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow();
        return header.split(";")[0].substring("XSRF-TOKEN=".length());
    }

    private record ProxyResponse(int status, HttpHeaders headers, byte[] body) {
    }
}
