package com.familion.economics.application.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Public API of the economics module exposed to other modules.
 */
public interface EconomicsApi {

    /**
     * Builds an immutable economic snapshot for a transaction amount on {@code date}.
     *
     * <p>When no exchange rate is available on or before {@code date}, the engine falls back to the
     * latest available rate for the index code; if no data exists at all, it logs a WARN and uses
     * {@code 1.000000}.
     */
    EconomicSnapshotData snapshotFor(
            FamilyEconomicContext ctx, LocalDate date, BigDecimal amount, String currencyCode);

    /**
     * Converts {@code amount} from {@code fromCurrency} to {@code toCurrency} on {@code date} by
     * pivoting through the base currency ({@code ARS}).
     *
     * <p>Missing quotes fall back to the latest available value, and then to {@code 1.000000} with a
     * WARN log.
     */
    BigDecimal convert(
            BigDecimal amount,
            String fromCurrency,
            String toCurrency,
            LocalDate date,
            String rateSource);

    /**
     * Returns the latest stored value for {@code indexCode} with {@code effective_date <= date}.
     */
    Optional<BigDecimal> valueAt(String indexCode, LocalDate date);

    /**
     * Returns the most recent stored value for {@code indexCode}.
     */
    Optional<BigDecimal> latestValue(String indexCode);
}
