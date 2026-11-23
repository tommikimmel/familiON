package com.familion.economics.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.familion.economics.domain.IndexCode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class ArgentinaDatosClientTest {

    private static final String BASE_URL = "https://api.argentinadatos.com";

    private MockRestServiceServer server;
    private ArgentinaDatosClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ArgentinaDatosClient(builder.build());
    }

    @Test
    void mapsHistoricalDollarsFromFixtureAndFiltersByStartDate() throws IOException {
        String fixture = readFixture("econ/argdatos-dolares-bolsa.json");
        server.expect(requestTo(BASE_URL + "/v1/cotizaciones/dolares/bolsa"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));

        List<ExternalIndexPoint> points = client.fetchHistoricalDollars(
                "bolsa", IndexCode.USD_MEP, LocalDate.of(2026, 11, 20));

        server.verify();
        assertThat(points)
                .containsExactly(
                        new ExternalIndexPoint(
                                IndexCode.USD_MEP,
                                LocalDate.of(2026, 11, 20),
                                new BigDecimal("1649.800000")),
                        new ExternalIndexPoint(
                                IndexCode.USD_MEP,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("1743.100000")));
    }

    @Test
    void mapsInflationNormalizedToFirstDayOfMonth() throws IOException {
        String fixture = readFixture("econ/argdatos-inflacion.json");
        server.expect(requestTo(BASE_URL + "/v1/finanzas/indices/inflacion"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));

        List<ExternalIndexPoint> points = client.fetchInflation(LocalDate.of(2026, 8, 15));

        server.verify();
        assertThat(points)
                .containsExactly(
                        new ExternalIndexPoint(
                                IndexCode.IPC_MENSUAL,
                                LocalDate.of(2026, 8, 1),
                                new BigDecimal("1.700000")),
                        new ExternalIndexPoint(
                                IndexCode.IPC_MENSUAL,
                                LocalDate.of(2026, 9, 1),
                                new BigDecimal("1.900000")),
                        new ExternalIndexPoint(
                                IndexCode.IPC_MENSUAL,
                                LocalDate.of(2026, 10, 1),
                                new BigDecimal("2.200000")));
    }

    @Test
    void skipsNonPositiveInflationEntriesToRespectDatabaseConstraint() {
        String payload =
                """
                [
                  {"fecha": "2026-08-31", "valor": 0},
                  {"fecha": "2026-09-30", "valor": -0.5},
                  {"fecha": "2026-10-31", "valor": 2.2}
                ]
                """;
        server.expect(requestTo(BASE_URL + "/v1/finanzas/indices/inflacion"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));

        List<ExternalIndexPoint> points = client.fetchInflation(LocalDate.of(2026, 1, 1));

        assertThat(points)
                .containsExactly(
                        new ExternalIndexPoint(
                                IndexCode.IPC_MENSUAL,
                                LocalDate.of(2026, 10, 1),
                                new BigDecimal("2.200000")));
    }

    @Test
    void mapsUvaValuesFromFixture() throws IOException {
        String fixture = readFixture("econ/argdatos-uva.json");
        server.expect(requestTo(BASE_URL + "/v1/finanzas/indices/uva"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));

        List<ExternalIndexPoint> points = client.fetchUva(LocalDate.of(2026, 11, 21));

        server.verify();
        assertThat(points)
                .containsExactly(
                        new ExternalIndexPoint(
                                IndexCode.UVA,
                                LocalDate.of(2026, 11, 21),
                                new BigDecimal("2206.780000")),
                        new ExternalIndexPoint(
                                IndexCode.UVA,
                                LocalDate.of(2026, 11, 22),
                                new BigDecimal("2206.780000")),
                        new ExternalIndexPoint(
                                IndexCode.UVA,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("2206.780000")));
    }

    @Test
    void propagatesHttpServerErrorToBeHandledBySyncService() {
        server.expect(requestTo(BASE_URL + "/v1/finanzas/indices/uva"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchUva(LocalDate.of(2026, 1, 1)))
                .isInstanceOf(HttpServerErrorException.class);
        server.verify();
    }

    private String readFixture(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
