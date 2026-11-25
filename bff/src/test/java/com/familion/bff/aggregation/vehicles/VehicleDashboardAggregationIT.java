package com.familion.bff.aggregation.vehicles;

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
class VehicleDashboardAggregationIT {

    private static final String VEHICLE_ID = "11111111-2222-3333-4444-555555555555";
    private static final String ACCESS_TOKEN = "jwt-vehicle-token";
    private static final String ACCESS_COOKIE = "FAMILION_AT=" + ACCESS_TOKEN;

    private static final String VEHICLE_JSON = """
            {
              "id": "11111111-2222-3333-4444-555555555555",
              "name": "Toyota Corolla",
              "plate": "AB123CD",
              "odometerKm": 45200,
              "alerts": [
                {"code": "VTV_DUE", "severity": "WARNING", "dueDate": "2026-12-10"},
                {"code": "SERVICE_KM", "severity": "INFO", "remainingKm": 800}
              ]
            }
            """;
    private static final String SUMMARY_JSON = """
            {
              "lens": "CONSTANT_BASE",
              "totalCost": "320000.0000",
              "costPerKm": "185.5000",
              "avgConsumptionLitersPer100Km": "7.8500"
            }
            """;
    private static final String FUEL_LOGS_PAGE_JSON = """
            {
              "content": [
                {"id": "fuel-1", "date": "2026-11-20", "liters": "42.5000", "totalCost": "52000.0000"},
                {"id": "fuel-2", "date": "2026-11-05", "liters": "38.0000", "totalCost": "46500.0000"}
              ],
              "totalElements": 2,
              "size": 5,
              "number": 0
            }
            """;
    private static final String MAINTENANCE_LOGS_PAGE_JSON = """
            {
              "content": [
                {"id": "maint-1", "date": "2026-10-15", "type": "OIL_CHANGE", "totalCost": "95000.0000"}
              ],
              "totalElements": 1,
              "size": 5,
              "number": 0
            }
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
    void aggregatesFullPayloadAndDerivesAlertsWithBearerAndParams() throws Exception {
        String summaryQuery = "?lens=CONSTANT_BASE&from=2026-01-01&to=2026-11-24";
        stubAllEndpoints(summaryQuery, 0);

        RawResponse response = getDashboard(
                "/bff/vehicles/" + VEHICLE_ID + "/dashboard" + summaryQuery,
                ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("vehicle").get("name").asText()).isEqualTo("Toyota Corolla");
        assertThat(body.get("summary").get("totalCost").asText()).isEqualTo("320000.0000");
        assertThat(body.get("recentFuelLogs").isArray()).isTrue();
        assertThat(body.get("recentFuelLogs")).hasSize(2);
        assertThat(body.get("recentFuelLogs").get(0).get("id").asText()).isEqualTo("fuel-1");
        assertThat(body.get("recentMaintenance").isArray()).isTrue();
        assertThat(body.get("recentMaintenance")).hasSize(1);
        assertThat(body.get("recentMaintenance").get(0).get("id").asText()).isEqualTo("maint-1");
        assertThat(body.get("alerts").isArray()).isTrue();
        assertThat(body.get("alerts")).hasSize(2);
        assertThat(body.get("alerts").get(0).get("code").asText()).isEqualTo("VTV_DUE");
        assertThat(body.get("alerts")).isEqualTo(body.get("vehicle").get("alerts"));

        verifyBearerAndNoCookie("/api/v1/vehicles/" + VEHICLE_ID);
        verifyBearerAndNoCookie("/api/v1/vehicles/" + VEHICLE_ID + "/summary" + summaryQuery);
        verifyBearerAndNoCookie("/api/v1/vehicles/" + VEHICLE_ID + "/fuel-logs?size=5");
        verifyBearerAndNoCookie("/api/v1/vehicles/" + VEHICLE_ID + "/maintenance-logs?size=5");
    }

    @Test
    void handlesAllOptionalQueryParametersOmitted() throws Exception {
        stubAllEndpoints("", 0);

        RawResponse response = getDashboard(
                "/bff/vehicles/" + VEHICLE_ID + "/dashboard",
                ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("vehicle").isObject()).isTrue();
        assertThat(body.get("summary").isObject()).isTrue();
        assertThat(body.get("recentFuelLogs")).hasSize(2);
        assertThat(body.get("recentMaintenance")).hasSize(1);
        assertThat(body.get("alerts")).hasSize(2);

        verifyBearerAndNoCookie("/api/v1/vehicles/" + VEHICLE_ID + "/summary");
    }

    @Test
    void vehicle404Returns404ProblemJson() throws Exception {
        stubAllEndpoints("", 0);
        coreMock.stubFor(get(urlEqualTo("/api/v1/vehicles/" + VEHICLE_ID))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Not Found\",\"status\":404}")));

        RawResponse response = getDashboard(
                "/bff/vehicles/" + VEHICLE_ID + "/dashboard",
                ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(404);
        assertThat(problem.get("title").asText()).isEqualTo("Not Found");
        assertThat(problem.get("instance").asText()).isEqualTo("/bff/vehicles/" + VEHICLE_ID + "/dashboard");
    }

    @Test
    void summary500DegradesSummaryToNullWhileRestSucceeds() throws Exception {
        stubAllEndpoints("", 0);
        coreMock.stubFor(get(urlEqualTo("/api/v1/vehicles/" + VEHICLE_ID + "/summary"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Internal Server Error\",\"status\":500}")));

        RawResponse response = getDashboard(
                "/bff/vehicles/" + VEHICLE_ID + "/dashboard",
                ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("vehicle").isObject()).isTrue();
        assertThat(body.has("summary")).isTrue();
        assertThat(body.get("summary").isNull()).isTrue();
        assertThat(body.get("recentFuelLogs")).hasSize(2);
        assertThat(body.get("recentMaintenance")).hasSize(1);
        assertThat(body.get("alerts")).hasSize(2);
    }

    @Test
    void vehicle500DegradesVehicleAndAlertsToNullWhileRestSucceeds() throws Exception {
        stubAllEndpoints("", 0);
        coreMock.stubFor(get(urlEqualTo("/api/v1/vehicles/" + VEHICLE_ID))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Internal Server Error\",\"status\":500}")));

        RawResponse response = getDashboard(
                "/bff/vehicles/" + VEHICLE_ID + "/dashboard",
                ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.has("vehicle")).isTrue();
        assertThat(body.get("vehicle").isNull()).isTrue();
        assertThat(body.has("alerts")).isTrue();
        assertThat(body.get("alerts").isNull()).isTrue();
        assertThat(body.get("summary").isObject()).isTrue();
        assertThat(body.get("recentFuelLogs")).hasSize(2);
        assertThat(body.get("recentMaintenance")).hasSize(1);
    }

    @Test
    void slowCoreCallTimesOutWithinGlobalDeadlineAndReturnsNullForThatField() throws Exception {
        stubJson("/api/v1/vehicles/" + VEHICLE_ID, VEHICLE_JSON, 0);
        stubJson("/api/v1/vehicles/" + VEHICLE_ID + "/summary", SUMMARY_JSON, 5_000);
        stubJson("/api/v1/vehicles/" + VEHICLE_ID + "/fuel-logs?size=5", FUEL_LOGS_PAGE_JSON, 0);
        stubJson("/api/v1/vehicles/" + VEHICLE_ID + "/maintenance-logs?size=5", MAINTENANCE_LOGS_PAGE_JSON, 0);

        long startNanos = System.nanoTime();
        RawResponse response = getDashboard(
                "/bff/vehicles/" + VEHICLE_ID + "/dashboard",
                ACCESS_COOKIE);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(response.status()).isEqualTo(200);
        assertThat(elapsedMs).isLessThan(3_500);

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("vehicle").isObject()).isTrue();
        assertThat(body.get("summary").isNull()).isTrue();
        assertThat(body.get("recentFuelLogs")).hasSize(2);
        assertThat(body.get("recentMaintenance")).hasSize(1);
        assertThat(body.get("alerts")).hasSize(2);
    }

    @Test
    void anyCore401Produces401ProblemJson() throws Exception {
        stubAllEndpoints("", 0);
        coreMock.stubFor(get(urlEqualTo("/api/v1/vehicles/" + VEHICLE_ID + "/fuel-logs?size=5"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"title\":\"Unauthorized\",\"status\":401}")));

        RawResponse response = getDashboard(
                "/bff/vehicles/" + VEHICLE_ID + "/dashboard",
                ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(401);
        assertThat(problem.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(problem.get("instance").asText()).isEqualTo("/bff/vehicles/" + VEHICLE_ID + "/dashboard");
    }

    @Test
    void missingAccessCookieReturns401WithoutCallingCore() throws Exception {
        RawResponse response = getDashboard(
                "/bff/vehicles/" + VEHICLE_ID + "/dashboard",
                null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(401);
        assertThat(problem.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(problem.get("instance").asText()).isEqualTo("/bff/vehicles/" + VEHICLE_ID + "/dashboard");

        coreMock.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void invalidUuidReturns400WithoutCallingCore() throws Exception {
        RawResponse response = getDashboard(
                "/bff/vehicles/not-a-uuid/dashboard",
                ACCESS_COOKIE);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        assertThat(response.headers().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(400);
        assertThat(problem.get("title").asText()).isEqualTo("Bad Request");
        assertThat(problem.get("instance").asText()).isEqualTo("/bff/vehicles/not-a-uuid/dashboard");

        RawResponse nonCanonical = getDashboard(
                "/bff/vehicles/1-2-3-4-5/dashboard",
                ACCESS_COOKIE);
        assertThat(nonCanonical.status()).isEqualTo(400);

        coreMock.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void executesCoreCallsInParallelInRoughlyMaxCallDuration() throws Exception {
        stubAllEndpoints("", 400);

        long startNanos = System.nanoTime();
        RawResponse response = getDashboard(
                "/bff/vehicles/" + VEHICLE_ID + "/dashboard",
                ACCESS_COOKIE);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(response.status()).isEqualTo(200);
        // 4 calls x 400ms = 1600ms sequentially; in parallel should finish close to ~400ms
        assertThat(elapsedMs).isLessThan(1_200);

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("vehicle").isObject()).isTrue();
        assertThat(body.get("summary").isObject()).isTrue();
        assertThat(body.get("recentFuelLogs")).hasSize(2);
        assertThat(body.get("recentMaintenance")).hasSize(1);
        assertThat(body.get("alerts")).hasSize(2);
    }

    private void stubAllEndpoints(String summaryQuery, int delayMs) {
        stubJson("/api/v1/vehicles/" + VEHICLE_ID, VEHICLE_JSON, delayMs);
        stubJson("/api/v1/vehicles/" + VEHICLE_ID + "/summary" + summaryQuery, SUMMARY_JSON, delayMs);
        stubJson("/api/v1/vehicles/" + VEHICLE_ID + "/fuel-logs?size=5", FUEL_LOGS_PAGE_JSON, delayMs);
        stubJson("/api/v1/vehicles/" + VEHICLE_ID + "/maintenance-logs?size=5", MAINTENANCE_LOGS_PAGE_JSON, delayMs);
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

    private RawResponse getDashboard(String uri, String cookieHeader) {
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
