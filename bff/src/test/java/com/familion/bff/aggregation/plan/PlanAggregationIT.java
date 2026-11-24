package com.familion.bff.aggregation.plan;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
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
class PlanAggregationIT {

    private static final String ACCESS_TOKEN = "jwt-plan-token";
    private static final String ACCESS_COOKIE = "FAMILION_AT=" + ACCESS_TOKEN;

    private static final String POSITIONS_JSON = """
            [{"currencyCode":"USD","balance":"1200.0000","weightedAvgCostArs":"1185.500000"}]
            """;
    private static final String INSTALLMENTS_JSON = """
            [{"id":"inst-1","description":"Notebook","totalInstallments":12,"status":"ACTIVE"}]
            """;
    private static final String COMMITMENT_JSON = """
            [{"month":"2026-11","totalAmount":"85000.0000"},{"month":"2026-12","totalAmount":"85000.0000"}]
            """;
    private static final String RECURRING_JSON = """
            [{"id":"rec-1","name":"Internet","baseAmount":"32000.0000","status":"PENDING"}]
            """;
    private static final String GOALS_JSON = """
            [{"id":"goal-1","name":"Vacaciones","targetAmount":"2000.0000","status":"ACTIVE"}]
            """;
    private static final String ALLOWANCES_JSON = """
            [{"id":"all-1","beneficiaryName":"Mateo","amount":"25000.0000"}]
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
    void aggregatesAllCoreCallsWithParamsAndBearer() throws Exception {
        stubAllEndpoints("FAMILY", "2026-11", 0);

        RawResponse response = getPlan("/bff/plan?view=FAMILY&month=2026-11", ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("positions").isArray()).isTrue();
        assertThat(body.get("positions").get(0).get("currencyCode").asText()).isEqualTo("USD");
        assertThat(body.get("installments").isArray()).isTrue();
        assertThat(body.get("installments").get(0).get("id").asText()).isEqualTo("inst-1");
        assertThat(body.get("commitment").isArray()).isTrue();
        assertThat(body.get("commitment")).hasSize(2);
        assertThat(body.get("recurring").isArray()).isTrue();
        assertThat(body.get("recurring").get(0).get("name").asText()).isEqualTo("Internet");
        assertThat(body.get("goals").isArray()).isTrue();
        assertThat(body.get("goals").get(0).get("name").asText()).isEqualTo("Vacaciones");
        assertThat(body.get("allowances").isArray()).isTrue();
        assertThat(body.get("allowances").get(0).get("beneficiaryName").asText()).isEqualTo("Mateo");

        verifyBearerAndNoCookie("/api/v1/currency-exchanges/positions?view=FAMILY");
        verifyBearerAndNoCookie("/api/v1/installment-plans?status=ACTIVE&view=FAMILY");
        verifyBearerAndNoCookie("/api/v1/installment-plans/commitment?months=6&view=FAMILY");
        verifyBearerAndNoCookie("/api/v1/recurring-expenses?month=2026-11");
        verifyBearerAndNoCookie("/api/v1/savings-goals?view=FAMILY&status=ACTIVE");
        verifyBearerAndNoCookie("/api/v1/family/allowances");
    }

    @Test
    void computesDefaultBuenosAiresMonthWhenMonthParamOmitted() throws Exception {
        String expectedMonth = YearMonth.now(PlanAggregationService.BUENOS_AIRES_ZONE).toString();
        stubAllEndpoints("PERSONAL", expectedMonth, 0);

        RawResponse response = getPlan("/bff/plan?view=PERSONAL", ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("positions")).hasSize(1);
        assertThat(body.get("installments")).hasSize(1);
        assertThat(body.get("commitment")).hasSize(2);
        assertThat(body.get("recurring")).hasSize(1);
        assertThat(body.get("goals")).hasSize(1);
        assertThat(body.get("allowances")).hasSize(1);

        verifyBearerAndNoCookie("/api/v1/currency-exchanges/positions?view=PERSONAL");
        verifyBearerAndNoCookie("/api/v1/installment-plans?status=ACTIVE&view=PERSONAL");
        verifyBearerAndNoCookie("/api/v1/installment-plans/commitment?months=6&view=PERSONAL");
        verifyBearerAndNoCookie("/api/v1/recurring-expenses?month=" + expectedMonth);
        verifyBearerAndNoCookie("/api/v1/savings-goals?view=PERSONAL&status=ACTIVE");
        verifyBearerAndNoCookie("/api/v1/family/allowances");
    }

    @Test
    void handlesAllOptionalQueryParametersOmitted() throws Exception {
        String expectedMonth = YearMonth.now(PlanAggregationService.BUENOS_AIRES_ZONE).toString();
        stubJson("/api/v1/currency-exchanges/positions", POSITIONS_JSON, 0);
        stubJson("/api/v1/installment-plans?status=ACTIVE", INSTALLMENTS_JSON, 0);
        stubJson("/api/v1/installment-plans/commitment?months=6", COMMITMENT_JSON, 0);
        stubJson("/api/v1/recurring-expenses?month=" + expectedMonth, RECURRING_JSON, 0);
        stubJson("/api/v1/savings-goals?status=ACTIVE", GOALS_JSON, 0);
        stubJson("/api/v1/family/allowances", ALLOWANCES_JSON, 0);

        RawResponse response = getPlan("/bff/plan", ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("positions")).hasSize(1);
        assertThat(body.get("installments")).hasSize(1);
        assertThat(body.get("commitment")).hasSize(2);
        assertThat(body.get("recurring")).hasSize(1);
        assertThat(body.get("goals")).hasSize(1);
        assertThat(body.get("allowances")).hasSize(1);

        verifyBearerAndNoCookie("/api/v1/recurring-expenses?month=" + expectedMonth);
    }

    @Test
    void partialFailureIncludingAllowances404DegradesToNullWhileRestSucceeds() throws Exception {
        stubJson("/api/v1/currency-exchanges/positions?view=FAMILY", POSITIONS_JSON, 0);
        stubJson("/api/v1/installment-plans?status=ACTIVE&view=FAMILY", INSTALLMENTS_JSON, 0);
        coreMock.stubFor(get(urlEqualTo("/api/v1/installment-plans/commitment?months=6&view=FAMILY"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Internal Server Error\",\"status\":500}")));
        stubJson("/api/v1/recurring-expenses?month=2026-11", RECURRING_JSON, 0);
        stubJson("/api/v1/savings-goals?view=FAMILY&status=ACTIVE", GOALS_JSON, 0);
        coreMock.stubFor(get(urlEqualTo("/api/v1/family/allowances"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Not Found\",\"status\":404}")));

        RawResponse response = getPlan("/bff/plan?view=FAMILY&month=2026-11", ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("positions")).hasSize(1);
        assertThat(body.get("installments")).hasSize(1);
        assertThat(body.has("commitment")).isTrue();
        assertThat(body.get("commitment").isNull()).isTrue();
        assertThat(body.get("recurring")).hasSize(1);
        assertThat(body.get("goals")).hasSize(1);
        assertThat(body.has("allowances")).isTrue();
        assertThat(body.get("allowances").isNull()).isTrue();
    }

    @Test
    void slowCoreCallTimesOutWithinGlobalDeadlineAndReturnsNullForThatField() throws Exception {
        stubJson("/api/v1/currency-exchanges/positions?view=FAMILY", POSITIONS_JSON, 0);
        stubJson("/api/v1/installment-plans?status=ACTIVE&view=FAMILY", INSTALLMENTS_JSON, 0);
        stubJson("/api/v1/installment-plans/commitment?months=6&view=FAMILY", COMMITMENT_JSON, 5_000);
        stubJson("/api/v1/recurring-expenses?month=2026-11", RECURRING_JSON, 0);
        stubJson("/api/v1/savings-goals?view=FAMILY&status=ACTIVE", GOALS_JSON, 0);
        stubJson("/api/v1/family/allowances", ALLOWANCES_JSON, 0);

        long startNanos = System.nanoTime();
        RawResponse response = getPlan("/bff/plan?view=FAMILY&month=2026-11", ACCESS_COOKIE);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(response.status()).isEqualTo(200);
        assertThat(elapsedMs).isLessThan(3_500);

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("positions")).hasSize(1);
        assertThat(body.get("installments")).hasSize(1);
        assertThat(body.get("commitment").isNull()).isTrue();
        assertThat(body.get("recurring")).hasSize(1);
        assertThat(body.get("goals")).hasSize(1);
        assertThat(body.get("allowances")).hasSize(1);
    }

    @Test
    void anyCore401Produces401ProblemJson() throws Exception {
        stubAllEndpoints("FAMILY", "2026-11", 0);
        coreMock.stubFor(get(urlEqualTo("/api/v1/savings-goals?view=FAMILY&status=ACTIVE"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Unauthorized\",\"status\":401}")));

        RawResponse response = getPlan("/bff/plan?view=FAMILY&month=2026-11", ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(401);
        assertThat(problem.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(problem.get("instance").asText()).isEqualTo("/bff/plan");
    }

    @Test
    void missingAccessCookieReturns401WithoutCallingCore() throws Exception {
        RawResponse response = getPlan("/bff/plan?view=FAMILY&month=2026-11", null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(401);
        assertThat(problem.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(problem.get("instance").asText()).isEqualTo("/bff/plan");

        coreMock.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void executesCoreCallsInParallelInRoughlyMaxCallDuration() throws Exception {
        stubAllEndpoints("FAMILY", "2026-11", 400);

        long startNanos = System.nanoTime();
        RawResponse response = getPlan("/bff/plan?view=FAMILY&month=2026-11", ACCESS_COOKIE);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(response.status()).isEqualTo(200);
        // 6 calls x 400ms = 2400ms sequentially; in parallel should finish close to ~400ms
        assertThat(elapsedMs).isLessThan(1_500);

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("positions")).hasSize(1);
        assertThat(body.get("installments")).hasSize(1);
        assertThat(body.get("commitment")).hasSize(2);
        assertThat(body.get("recurring")).hasSize(1);
        assertThat(body.get("goals")).hasSize(1);
        assertThat(body.get("allowances")).hasSize(1);
    }

    private void stubAllEndpoints(String view, String month, int delayMs) {
        stubJson("/api/v1/currency-exchanges/positions?view=" + view, POSITIONS_JSON, delayMs);
        stubJson("/api/v1/installment-plans?status=ACTIVE&view=" + view, INSTALLMENTS_JSON, delayMs);
        stubJson("/api/v1/installment-plans/commitment?months=6&view=" + view, COMMITMENT_JSON, delayMs);
        stubJson("/api/v1/recurring-expenses?month=" + month, RECURRING_JSON, delayMs);
        stubJson("/api/v1/savings-goals?view=" + view + "&status=ACTIVE", GOALS_JSON, delayMs);
        stubJson("/api/v1/family/allowances", ALLOWANCES_JSON, delayMs);
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

    private RawResponse getPlan(String uri, String cookieHeader) {
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
