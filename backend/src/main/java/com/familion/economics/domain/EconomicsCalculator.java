package com.familion.economics.domain;

import com.familion.common.money.Money;
import com.familion.economics.application.api.EconomicSnapshotData;
import com.familion.economics.application.api.FamilyEconomicContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure domain calculation engine for economic snapshots and currency conversions.
 *
 * <p>All monetary amounts are rounded to scale 4 ({@link Money#SCALE}) and exchange rates/indices to
 * scale 6 ({@link Money#RATE_SCALE}) using {@link java.math.RoundingMode#HALF_EVEN}.
 *
 * <p>Rate fallback policy: if an index has no value on or before the requested date, the calculator
 * falls back to the latest known value for that index; if no data exists at all, it logs a WARN
 * ({@code "No economic data for {code}; using 1"}) and returns {@code 1.000000} so operations never
 * fail when offline or before initial sync.
 */
public final class EconomicsCalculator {

    private static final Logger log = LoggerFactory.getLogger(EconomicsCalculator.class);
    private static final String IMPLICIT_BASE_CURRENCY = "ARS";

    public interface IndexLookup {
        Optional<BigDecimal> valueAt(IndexCode indexCode, LocalDate date);

        Optional<BigDecimal> latestValue(IndexCode indexCode);
    }

    public EconomicSnapshotData snapshotFor(
            FamilyEconomicContext ctx,
            LocalDate date,
            BigDecimal amount,
            String currencyCode,
            IndexLookup lookup) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(ctx.baseCurrency(), "baseCurrency must not be null");
        Objects.requireNonNull(ctx.referenceCurrency(), "referenceCurrency must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Objects.requireNonNull(lookup, "lookup must not be null");

        String baseCurrency = normalizeCurrency(ctx.baseCurrency());
        String referenceCurrency = normalizeCurrency(ctx.referenceCurrency());
        String txCurrency = normalizeCurrency(currencyCode);

        BigDecimal baseCurrencyAmount;
        if (txCurrency.equals(baseCurrency)) {
            baseCurrencyAmount = Money.scale(amount);
        } else {
            BigDecimal txRate =
                    resolveRateToBase(txCurrency, baseCurrency, date, ctx.referenceRateSource(), lookup);
            baseCurrencyAmount = Money.scale(amount.multiply(txRate));
        }

        BigDecimal referenceExchangeRate = resolveRateToBase(
                referenceCurrency, baseCurrency, date, ctx.referenceRateSource(), lookup);
        BigDecimal amountInReference = Money.divide(baseCurrencyAmount, referenceExchangeRate);

        BigDecimal ipcIndexValue =
                lookup.valueAt(IndexCode.IPC_ACUMULADO, date).map(Money::rate).orElse(null);
        BigDecimal uvaValue = lookup.valueAt(IndexCode.UVA, date).map(Money::rate).orElse(null);

        return new EconomicSnapshotData(
                referenceCurrency,
                ctx.referenceRateSource(),
                referenceExchangeRate,
                amountInReference,
                baseCurrencyAmount,
                ipcIndexValue,
                uvaValue,
                date);
    }

    public BigDecimal convert(
            BigDecimal amount,
            String fromCurrency,
            String toCurrency,
            LocalDate date,
            String rateSource,
            IndexLookup lookup) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(fromCurrency, "fromCurrency must not be null");
        Objects.requireNonNull(toCurrency, "toCurrency must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(lookup, "lookup must not be null");

        String from = normalizeCurrency(fromCurrency);
        String to = normalizeCurrency(toCurrency);
        if (from.equals(to)) {
            return Money.scale(amount);
        }

        BigDecimal fromRate =
                resolveRateToBase(from, IMPLICIT_BASE_CURRENCY, date, rateSource, lookup);
        BigDecimal toRate =
                resolveRateToBase(to, IMPLICIT_BASE_CURRENCY, date, rateSource, lookup);
        return Money.divide(amount.multiply(fromRate), toRate);
    }

    private BigDecimal resolveRateToBase(
            String currency,
            String baseCurrency,
            LocalDate date,
            String rateSource,
            IndexLookup lookup) {
        if (currency.equals(baseCurrency)) {
            return Money.rate(BigDecimal.ONE);
        }
        Optional<IndexCode> resolvedCode = resolveIndexCode(currency, rateSource);
        if (resolvedCode.isEmpty()) {
            log.warn("No economic data for {}; using 1", currency);
            return Money.rate(BigDecimal.ONE);
        }
        IndexCode code = resolvedCode.get();
        Optional<BigDecimal> value = lookup.valueAt(code, date);
        if (value.isEmpty()) {
            value = lookup.latestValue(code);
        }
        if (value.isEmpty()) {
            log.warn("No economic data for {}; using 1", code.name());
            return Money.rate(BigDecimal.ONE);
        }
        return Money.rate(value.get());
    }

    private Optional<IndexCode> resolveIndexCode(String currency, String rateSource) {
        return switch (currency) {
            case "USD" -> Optional.of(resolveUsdIndexCode(rateSource));
            case "USDT" -> Optional.of(IndexCode.USD_MEP);
            case "EUR" -> Optional.of(IndexCode.EUR_OFICIAL);
            case "UVA" -> Optional.of(IndexCode.UVA);
            default -> Optional.empty();
        };
    }

    private IndexCode resolveUsdIndexCode(String rateSource) {
        if (rateSource != null) {
            String normalized = rateSource.trim().toUpperCase(Locale.ROOT);
            if (normalized.startsWith("USD_")) {
                return IndexCode.fromCode(normalized).orElse(IndexCode.USD_MEP);
            }
        }
        return IndexCode.USD_MEP;
    }

    private static String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
