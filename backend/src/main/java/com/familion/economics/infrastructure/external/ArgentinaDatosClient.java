package com.familion.economics.infrastructure.external;

import com.familion.common.money.Money;
import com.familion.economics.domain.IndexCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for ArgentinaDatos ({@code https://api.argentinadatos.com}).
 *
 * <p>Provides historical dollar sell quotes ({@code GET /v1/cotizaciones/dolares/{casa}}), monthly
 * inflation percentages ({@code GET /v1/finanzas/indices/inflacion}, normalized to the first day of
 * each month), and daily UVA values ({@code GET /v1/finanzas/indices/uva}).
 */
@Component
public class ArgentinaDatosClient {

    private final RestClient restClient;

    public ArgentinaDatosClient(
            @Qualifier("argentinaDatosRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List<ExternalIndexPoint> fetchHistoricalDollars(
            String casa, IndexCode targetCode, LocalDate fromInclusive) {
        Objects.requireNonNull(casa, "casa must not be null");
        Objects.requireNonNull(targetCode, "targetCode must not be null");
        Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");

        ArgDatosDollarQuote[] quotes = restClient
                .get()
                .uri("/v1/cotizaciones/dolares/{casa}", casa)
                .retrieve()
                .body(ArgDatosDollarQuote[].class);
        if (quotes == null || quotes.length == 0) {
            return List.of();
        }
        Map<LocalDate, ExternalIndexPoint> byDate = new LinkedHashMap<>();
        for (ArgDatosDollarQuote quote : quotes) {
            if (quote == null || quote.fecha() == null || quote.venta() == null) {
                continue;
            }
            if (quote.fecha().isBefore(fromInclusive)) {
                continue;
            }
            BigDecimal scaled = Money.rate(quote.venta());
            if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            byDate.put(quote.fecha(), new ExternalIndexPoint(targetCode, quote.fecha(), scaled));
        }
        return List.copyOf(byDate.values());
    }

    public List<ExternalIndexPoint> fetchInflation(LocalDate fromInclusive) {
        Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");
        LocalDate normalizedCutoff = fromInclusive.withDayOfMonth(1);

        ArgDatosIndexPoint[] points = restClient
                .get()
                .uri("/v1/finanzas/indices/inflacion")
                .retrieve()
                .body(ArgDatosIndexPoint[].class);
        if (points == null || points.length == 0) {
            return List.of();
        }
        Map<LocalDate, ExternalIndexPoint> byMonth = new LinkedHashMap<>();
        for (ArgDatosIndexPoint point : points) {
            if (point == null || point.fecha() == null || point.valor() == null) {
                continue;
            }
            LocalDate firstOfMonth = point.fecha().withDayOfMonth(1);
            if (firstOfMonth.isBefore(normalizedCutoff)) {
                continue;
            }
            BigDecimal scaled = Money.rate(point.valor());
            if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            byMonth.put(
                    firstOfMonth,
                    new ExternalIndexPoint(IndexCode.IPC_MENSUAL, firstOfMonth, scaled));
        }
        return List.copyOf(byMonth.values());
    }

    public List<ExternalIndexPoint> fetchUva(LocalDate fromInclusive) {
        Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");

        ArgDatosIndexPoint[] points = restClient
                .get()
                .uri("/v1/finanzas/indices/uva")
                .retrieve()
                .body(ArgDatosIndexPoint[].class);
        if (points == null || points.length == 0) {
            return List.of();
        }
        Map<LocalDate, ExternalIndexPoint> byDate = new LinkedHashMap<>();
        for (ArgDatosIndexPoint point : points) {
            if (point == null || point.fecha() == null || point.valor() == null) {
                continue;
            }
            if (point.fecha().isBefore(fromInclusive)) {
                continue;
            }
            BigDecimal scaled = Money.rate(point.valor());
            if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            byDate.put(point.fecha(), new ExternalIndexPoint(IndexCode.UVA, point.fecha(), scaled));
        }
        return List.copyOf(byDate.values());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArgDatosDollarQuote(String casa, BigDecimal compra, BigDecimal venta, LocalDate fecha) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArgDatosIndexPoint(LocalDate fecha, BigDecimal valor) {}
}
