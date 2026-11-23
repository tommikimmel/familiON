package com.familion.bff.ratelimit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitFailOpenIT {

    private static final int CLOSED_PORT = findClosedPort();

    @RegisterExtension
    static WireMockExtension coreMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("familion.core-url", coreMock::baseUrl);
        registry.add("familion.rate-limit-per-min", () -> "1");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> CLOSED_PORT);
        registry.add("spring.data.redis.connect-timeout", () -> "200ms");
        registry.add("spring.data.redis.timeout", () -> "200ms");
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
    void requestsPassThroughWhenRedisIsDown() {
        for (int i = 0; i < 5; i++) {
            int status = client.get()
                    .uri("/bff/api/ping")
                    .exchange((req, res) -> res.getStatusCode().value());
            assertThat(status).isEqualTo(200);
        }
    }

    private static int findClosedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            return 1;
        }
    }
}
