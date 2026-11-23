package com.familion.economics.infrastructure.external;

import com.familion.common.money.Money;
import com.familion.economics.domain.IndexCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for DolarApi ({@code https://dolarapi.com}).
 *
 * <p>Fetches latest USD sell quotes ({@code oficial -> USD_OFICIAL}, {@code blue -> USD_BLUE},
 * {@code bolsa -> USD_MEP}) from {@code GET /v1/dolares} and official EUR sell quote from
 * {@code GET /v1/cotizaciones/eur}. DolarApi does not expose an EUR blue endpoint (returns 404),
 * so {@link IndexCode#EUR_BLUE} is only populated via manual entry.
 */
@Component
public class DolarApiClient {

    public static final ZoneId ARGENTINA_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final Logger log = LoggerFactory.getLogger(DolarApiClient.class);

    private static final Map<String, IndexCode> DOLLAR_CASA_TO_CODE = Map.of(
            "oficial", IndexCode.USD_OFICIAL,
            "blue", IndexCode.USD_BLUE,
            "bolsa", IndexCode.USD_MEP);

    private final RestClient restClient;
    private final Clock clock;

    public DolarApiClient(@Qualifier("dolarApiRestClient") RestClient restClient, Clock clock) {
        this.restClient = restClient;
        this.clock = clock;
    }

    public List<ExternalIndexPoint> fetchLatestDollars() {
        DolarApiQuote[] quotes =
                restClient.get().uri("/v1/dolares").retrieve().body(DolarApiQuote[].class);
        if (quotes == null || quotes.length == 0) {
            return List.of();
        }
        List<ExternalIndexPoint> results = new ArrayList<>();
        for (DolarApiQuote quote : quotes) {
            if (quote == null || quote.casa() == null) {
                continue;
            }
            IndexCode code = DOLLAR_CASA_TO_CODE.get(quote.casa().trim().toLowerCase(Locale.ROOT));
            if (code == null) {
                continue;
            }
            toPoint(code, quote).ifPresent(results::add);
        }
        return List.copyOf(results);
    }

    public Optional<ExternalIndexPoint> fetchLatestEur() {
        DolarApiQuote quote =
                restClient.get().uri("/v1/cotizaciones/eur").retrieve().body(DolarApiQuote.class);
        if (quote == null) {
            return Optional.empty();
        }
        return toPoint(IndexCode.EUR_OFICIAL, quote);
    }

    private Optional<ExternalIndexPoint> toPoint(IndexCode code, DolarApiQuote quote) {
        if (quote.venta() == null) {
            log.warn("Skipping {} from DolarApi: missing sell value (venta)", code);
            return Optional.empty();
        }
        BigDecimal scaled = Money.rate(quote.venta());
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Skipping {} from DolarApi: non-positive value {}", code, scaled);
            return Optional.empty();
        }
        LocalDate effectiveDate = parseArgentinaDate(quote.fechaActualizacion());
        return Optional.of(new ExternalIndexPoint(code, effectiveDate, scaled));
    }

    private LocalDate parseArgentinaDate(String rawTimestamp) {
        if (rawTimestamp != null && !rawTimestamp.isBlank()) {
            try {
                return OffsetDateTime.parse(rawTimestamp.trim())
                        .atZoneSameInstant(ARGENTINA_ZONE)
                        .toLocalDate();
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDate.parse(rawTimestamp.trim());
                } catch (DateTimeParseException ex) {
                    log.warn(
                            "Could not parse DolarApi fechaActualizacion '{}'; falling back to today in AR",
                            rawTimestamp);
                }
            }
        }
        return LocalDate.now(clock.withZone(ARGENTINA_ZONE));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DolarApiQuote(
            String moneda,
            String casa,
            String nombre,
            BigDecimal compra,
            BigDecimal venta,
            String fechaActualizacion) {}
}
