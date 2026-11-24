package com.familion.bff.aggregation.home;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HomeAggregationIT {

    private static final String ACCESS_TOKEN = "jwt-home-token";
    private static final String ACCESS_COOKIE = "FAMILION_AT=" + ACCESS_TOKEN;

    private static final String SUMMARY_JSON = """
            {"view":"FAMILY","lens":"CONSTANT_BASE","income":"150000.0000","expense":"95000.0000","balance":"55000.0000"}
            """;
    private static final String BY_CATEGORY_JSON = """
            {"total":"95000.0000","slices":[{"name":"Supermercado","amount":"45000.0000"}]}
            """;
    private static final String BY_PERSON_JSON = """
            {"total":"95000.0000","slices":[{"fullName":"Ana","amount":"50000.0000"}]}
            """;
    private static final String TRANSACTIONS_PAGE_JSON = """
            {"content":[{"id":"tx-1","amount":"1200.0000"},{"id":"tx-2","amount":"3400.0000"}],"totalElements":2,"size":10,"number":0}
            """;
    private static final String LATEST_RATES_JSON = """
            [{"indexCode":"USD_BLUE","effectiveDate":"2026-11-23","value":"1210.000000","source":"API"}]
            """;
    private static final String UNREAD_COUNT_JSON = """
            {"count":5}
            """;

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
    private ObjectMapper objectMapper;

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
    void aggregatesAllCoreCallsForFamilyViewWithBearerAndParams() throws Exception {
        String analyticsQuery = "?view=FAMILY&lens=CONSTANT_BASE&granularity=MONTH&date=2026-11-23";
        stubAllFamilyEndpoints(analyticsQuery, 0);

        RawResponse response = getHome("/bff/home" + analyticsQuery, ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("summary").get("income").asText()).isEqualTo("150000.0000");
        assertThat(body.get("byCategory").get("slices").get(0).get("name").asText()).isEqualTo("Supermercado");
        assertThat(body.get("byPerson").get("slices").get(0).get("fullName").asText()).isEqualTo("Ana");
        assertThat(body.get("recentTransactions").isArray()).isTrue();
        assertThat(body.get("recentTransactions")).hasSize(2);
        assertThat(body.get("recentTransactions").get(0).get("id").asText()).isEqualTo("tx-1");
        assertThat(body.get("latestRates").isArray()).isTrue();
        assertThat(body.get("latestRates").get(0).get("indexCode").asText()).isEqualTo("USD_BLUE");
        assertThat(body.get("unreadNotifications").asLong()).isEqualTo(5L);

        verifyBearerAndNoCookie("/api/v1/analytics/summary" + analyticsQuery);
        verifyBearerAndNoCookie("/api/v1/analytics/by-category" + analyticsQuery);
        verifyBearerAndNoCookie("/api/v1/analytics/by-person" + analyticsQuery);
        verifyBearerAndNoCookie(
                "/api/v1/transactions?view=FAMILY&lens=CONSTANT_BASE&size=10&sort=transactionDate,desc");
        verifyBearerAndNoCookie("/api/v1/economics/indices/latest");
        verifyBearerAndNoCookie("/api/v1/notifications/unread-count");
    }

    @Test
    void personalViewDoesNotCallByPerson() throws Exception {
        String analyticsQuery = "?view=PERSONAL&lens=NOMINAL&granularity=MONTH&date=2026-11-23";
        stubJson("/api/v1/analytics/summary" + analyticsQuery, SUMMARY_JSON, 0);
        stubJson("/api/v1/analytics/by-category" + analyticsQuery, BY_CATEGORY_JSON, 0);
        stubJson(
                "/api/v1/transactions?view=PERSONAL&lens=NOMINAL&size=10&sort=transactionDate,desc",
                TRANSACTIONS_PAGE_JSON,
                0);
        stubJson("/api/v1/economics/indices/latest", LATEST_RATES_JSON, 0);
        stubJson("/api/v1/notifications/unread-count", UNREAD_COUNT_JSON, 0);

        RawResponse response = getHome("/bff/home" + analyticsQuery, ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("summary").isObject()).isTrue();
        assertThat(body.get("byCategory").isObject()).isTrue();
        assertThat(body.has("byPerson")).isTrue();
        assertThat(body.get("byPerson").isNull()).isTrue();
        assertThat(body.get("recentTransactions")).hasSize(2);
        assertThat(body.get("latestRates")).hasSize(1);
        assertThat(body.get("unreadNotifications").asLong()).isEqualTo(5L);

        coreMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/analytics/by-person")));
    }

    @Test
    void byPersonForbiddenDegradesToNullWhileRestSucceeds() throws Exception {
        String analyticsQuery = "?view=FAMILY&lens=NOMINAL&granularity=MONTH&date=2026-11-23";
        stubJson("/api/v1/analytics/summary" + analyticsQuery, SUMMARY_JSON, 0);
        stubJson("/api/v1/analytics/by-category" + analyticsQuery, BY_CATEGORY_JSON, 0);
        coreMock.stubFor(get(urlEqualTo("/api/v1/analytics/by-person" + analyticsQuery))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Forbidden\",\"status\":403}")));
        stubJson(
                "/api/v1/transactions?view=FAMILY&lens=NOMINAL&size=10&sort=transactionDate,desc",
                TRANSACTIONS_PAGE_JSON,
                0);
        stubJson("/api/v1/economics/indices/latest", LATEST_RATES_JSON, 0);
        coreMock.stubFor(get(urlEqualTo("/api/v1/notifications/unread-count"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Not Found\",\"status\":404}")));

        RawResponse response = getHome("/bff/home" + analyticsQuery, ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("summary").isObject()).isTrue();
        assertThat(body.get("byCategory").isObject()).isTrue();
        assertThat(body.get("byPerson").isNull()).isTrue();
        assertThat(body.get("recentTransactions")).hasSize(2);
        assertThat(body.get("latestRates")).hasSize(1);
        assertThat(body.get("unreadNotifications").isNull()).isTrue();
    }

    @Test
    void slowCoreCallTimesOutWithinGlobalDeadlineAndReturnsNullForThatField() throws Exception {
        String analyticsQuery = "?view=FAMILY&lens=NOMINAL&granularity=MONTH&date=2026-11-23";
        stubJson("/api/v1/analytics/summary" + analyticsQuery, SUMMARY_JSON, 0);
        stubJson("/api/v1/analytics/by-category" + analyticsQuery, BY_CATEGORY_JSON, 0);
        stubJson("/api/v1/analytics/by-person" + analyticsQuery, BY_PERSON_JSON, 0);
        stubJson(
                "/api/v1/transactions?view=FAMILY&lens=NOMINAL&size=10&sort=transactionDate,desc",
                TRANSACTIONS_PAGE_JSON,
                0);
        stubJson("/api/v1/economics/indices/latest", LATEST_RATES_JSON, 5_000);
        stubJson("/api/v1/notifications/unread-count", UNREAD_COUNT_JSON, 0);

        long startNanos = System.nanoTime();
        RawResponse response = getHome("/bff/home" + analyticsQuery, ACCESS_COOKIE);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(response.status()).isEqualTo(200);
        assertThat(elapsedMs).isLessThan(3_500);

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("summary").isObject()).isTrue();
        assertThat(body.get("byCategory").isObject()).isTrue();
        assertThat(body.get("byPerson").isObject()).isTrue();
        assertThat(body.get("recentTransactions")).hasSize(2);
        assertThat(body.get("latestRates").isNull()).isTrue();
        assertThat(body.get("unreadNotifications").asLong()).isEqualTo(5L);
    }

    @Test
    void anyCore401Produces401ProblemJson() throws Exception {
        String analyticsQuery = "?view=FAMILY&lens=NOMINAL&granularity=MONTH&date=2026-11-23";
        stubAllFamilyEndpoints(analyticsQuery, 0);
        coreMock.stubFor(get(urlEqualTo("/api/v1/analytics/by-category" + analyticsQuery))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Unauthorized\",\"status\":401}")));

        RawResponse response = getHome("/bff/home" + analyticsQuery, ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(401);
        assertThat(problem.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(problem.get("instance").asText()).isEqualTo("/bff/home");
    }

    @Test
    void missingAccessCookieReturns401WithoutCallingCore() throws Exception {
        RawResponse response = getHome("/bff/home?view=FAMILY", null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(401);
        assertThat(problem.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(problem.get("instance").asText()).isEqualTo("/bff/home");

        coreMock.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void executesCoreCallsInParallelInRoughlyMaxCallDuration() throws Exception {
        String analyticsQuery = "?view=FAMILY&lens=NOMINAL&granularity=MONTH&date=2026-11-23";
        stubAllFamilyEndpoints(analyticsQuery, 400);

        long startNanos = System.nanoTime();
        RawResponse response = getHome("/bff/home" + analyticsQuery, ACCESS_COOKIE);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(response.status()).isEqualTo(200);
        // 6 calls x 400ms = 2400ms sequentially; in parallel should finish close to ~400ms
        assertThat(elapsedMs).isLessThan(1_500);

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("summary").isObject()).isTrue();
        assertThat(body.get("byCategory").isObject()).isTrue();
        assertThat(body.get("byPerson").isObject()).isTrue();
        assertThat(body.get("recentTransactions")).hasSize(2);
        assertThat(body.get("latestRates")).hasSize(1);
        assertThat(body.get("unreadNotifications").asLong()).isEqualTo(5L);
    }

    @Test
    void handlesAllOptionalQueryParametersOmitted() throws Exception {
        stubJson("/api/v1/analytics/summary", SUMMARY_JSON, 0);
        stubJson("/api/v1/analytics/by-category", BY_CATEGORY_JSON, 0);
        stubJson("/api/v1/transactions?size=10&sort=transactionDate,desc", TRANSACTIONS_PAGE_JSON, 0);
        stubJson("/api/v1/economics/indices/latest", LATEST_RATES_JSON, 0);
        stubJson("/api/v1/notifications/unread-count", UNREAD_COUNT_JSON, 0);

        RawResponse response = getHome("/bff/home", ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("summary").isObject()).isTrue();
        assertThat(body.get("byCategory").isObject()).isTrue();
        assertThat(body.get("byPerson").isNull()).isTrue();
        assertThat(body.get("recentTransactions")).hasSize(2);
        assertThat(body.get("latestRates")).hasSize(1);
        assertThat(body.get("unreadNotifications").asLong()).isEqualTo(5L);

        coreMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/analytics/by-person")));
    }

    private void stubAllFamilyEndpoints(String analyticsQuery, int delayMs) {
        stubJson("/api/v1/analytics/summary" + analyticsQuery, SUMMARY_JSON, delayMs);
        stubJson("/api/v1/analytics/by-category" + analyticsQuery, BY_CATEGORY_JSON, delayMs);
        stubJson("/api/v1/analytics/by-person" + analyticsQuery, BY_PERSON_JSON, delayMs);
        String transactionsPath = analyticsQuery.contains("CONSTANT_BASE")
                ? "/api/v1/transactions?view=FAMILY&lens=CONSTANT_BASE&size=10&sort=transactionDate,desc"
                : "/api/v1/transactions?view=FAMILY&lens=NOMINAL&size=10&sort=transactionDate,desc";
        stubJson(transactionsPath, TRANSACTIONS_PAGE_JSON, delayMs);
        stubJson("/api/v1/economics/indices/latest", LATEST_RATES_JSON, delayMs);
        stubJson("/api/v1/notifications/unread-count", UNREAD_COUNT_JSON, delayMs);
    }

    private void stubJson(String url, String body, int delayMs) {
        var responseBuilder = aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
        if (delayMs > 0) {
            responseBuilder.withFixedDelay(delayMs);
        }
        coreMock.stubFor(get(urlEqualTo(url)).willReturn(responseBuilder));
    }

    private void verifyBearerAndNoCookie(String expectedUrl) {
        coreMock.verify(getRequestedFor(urlEqualTo(expectedUrl))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
                .withHeader("Cookie", absent()));
    }

    private RawResponse getHome(String uri, String cookieHeader) {
        var spec = client.get().uri(uri);
        if (cookieHeader != null) {
            spec = spec.header(HttpHeaders.COOKIE, cookieHeader);
        }
        return spec.exchange((req, res) -> new RawResponse(
                res.getStatusCode().value(),
                res.getHeaders(),
                new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private record RawResponse(int status, HttpHeaders headers, String body) {
    }
}
