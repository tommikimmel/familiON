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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class DolarApiClientTest {

    private static final String BASE_URL = "https://dolarapi.com";
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), ZoneOffset.UTC);

    private MockRestServiceServer server;
    private DolarApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DolarApiClient(builder.build(), FIXED_CLOCK);
    }

    @Test
    void mapsOficialBlueAndBolsaSellPricesFromRealFixture() throws IOException {
        String fixture = readFixture("econ/dolarapi-dolares.json");
        server.expect(requestTo(BASE_URL + "/v1/dolares"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));

        List<ExternalIndexPoint> points = client.fetchLatestDollars();

        server.verify();
        assertThat(points)
                .containsExactly(
                        new ExternalIndexPoint(
                                IndexCode.USD_OFICIAL,
                                LocalDate.of(2026, 11, 20),
                                new BigDecimal("1600.000000")),
                        new ExternalIndexPoint(
                                IndexCode.USD_BLUE,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("1675.000000")),
                        new ExternalIndexPoint(
                                IndexCode.USD_MEP,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("1743.100000")));
    }

    @Test
    void mapsEurOfficialSellPriceFromRealFixture() throws IOException {
        String fixture = readFixture("econ/dolarapi-eur.json");
        server.expect(requestTo(BASE_URL + "/v1/cotizaciones/eur"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));

        Optional<ExternalIndexPoint> eur = client.fetchLatestEur();

        server.verify();
        assertThat(eur)
                .contains(
                        new ExternalIndexPoint(
                                IndexCode.EUR_OFICIAL,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("1735.630000")));
    }

    @Test
    void convertsUtcTimestampToArgentinaDateAcrossMidnight() {
        String payload =
                """
                [
                  {
                    "moneda": "USD",
                    "casa": "bolsa",
                    "nombre": "Bolsa",
                    "compra": 1700,
                    "venta": 1750.25,
                    "fechaActualizacion": "2026-11-24T01:30:00.000Z"
                  }
                ]
                """;
        server.expect(requestTo(BASE_URL + "/v1/dolares"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));

        List<ExternalIndexPoint> points = client.fetchLatestDollars();

        assertThat(points)
                .containsExactly(
                        new ExternalIndexPoint(
                                IndexCode.USD_MEP,
                                LocalDate.of(2026, 11, 23),
                                new BigDecimal("1750.250000")));
    }

    @Test
    void propagatesHttpServerErrorToBeHandledBySyncService() {
        server.expect(requestTo(BASE_URL + "/v1/dolares"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchLatestDollars())
                .isInstanceOf(HttpServerErrorException.class);
        server.verify();
    }

    private String readFixture(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
