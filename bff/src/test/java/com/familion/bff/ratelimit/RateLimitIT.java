package com.familion.bff.ratelimit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @RegisterExtension
    static WireMockExtension coreMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("familion.core-url", coreMock::baseUrl);
        registry.add("familion.rate-limit-per-min", () -> "3");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        coreMock.resetAll();
        coreMock.stubFor(get(urlEqualTo("/api/v1/ping"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true}")));

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Test
    void enforcesPerUserLimitAndReturns429ProblemJsonWithRetryAfter() {
        String userOneJwt = fakeJwtWithSub("user-1");
        String userTwoJwt = fakeJwtWithSub("user-2");

        for (int i = 0; i < 3; i++) {
            ResponseData ok = getWithCookie("/bff/api/ping", "FAMILION_AT=" + userOneJwt, null);
            assertThat(ok.status()).isEqualTo(200);
        }

        ResponseData throttled = getWithCookie("/bff/api/ping", "FAMILION_AT=" + userOneJwt, null);
        assertThat(throttled.status()).isEqualTo(429);
        assertThat(throttled.contentType()).contains("application/problem+json");
        assertThat(throttled.retryAfter()).isNotBlank();
        assertThat(Long.parseLong(throttled.retryAfter())).isPositive();
        assertThat(throttled.body()).contains("\"status\":429");

        ResponseData otherUser = getWithCookie("/bff/api/ping", "FAMILION_AT=" + userTwoJwt, null);
        assertThat(otherUser.status()).isEqualTo(200);
    }

    @Test
    void enforcesPerIpLimitAndExcludesActuatorEndpoints() {
        String clientIp = "198.51.100.77";

        for (int i = 0; i < 3; i++) {
            ResponseData ok = getWithCookie("/bff/api/ping", null, clientIp + ", 10.0.0.1");
            assertThat(ok.status()).isEqualTo(200);
        }

        ResponseData throttled = getWithCookie("/bff/api/ping", null, clientIp + ", 10.0.0.1");
        assertThat(throttled.status()).isEqualTo(429);

        ResponseData health = getWithCookie("/actuator/health", null, clientIp);
        assertThat(health.status()).isEqualTo(200);
    }

    private ResponseData getWithCookie(String path, String cookie, String forwardedFor) {
        RestClient.RequestHeadersSpec<?> spec = client.get().uri(path);
        if (cookie != null) {
            spec = spec.header(HttpHeaders.COOKIE, cookie);
        }
        if (forwardedFor != null) {
            spec = spec.header("X-Forwarded-For", forwardedFor);
        }
        return spec.exchange((req, res) -> new ResponseData(
                res.getStatusCode().value(),
                res.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private String fakeJwtWithSub(String sub) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + sub + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }

    private record ResponseData(int status, String contentType, String retryAfter, String body) {
    }
}
