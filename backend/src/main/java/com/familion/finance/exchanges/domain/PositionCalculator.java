package com.familion.finance.exchanges.domain;

import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Pure domain calculator for foreign-currency holdings using the chronological weighted average
 * cost (PPP) method, valued against current reference exchange rates.
 */
public final class PositionCalculator {

    private static final int INTERMEDIATE_SCALE = 12;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String DEFAULT_USD_INDEX = "USD_MEP";
    private static final String DEFAULT_EUR_INDEX = "EUR_OFICIAL";

    public record ExchangeLeg(
            String sourceCurrency,
            BigDecimal sourceAmount,
            String targetCurrency,
            BigDecimal targetAmount) {}

    public record PositionSnapshot(
            String currencyCode,
            BigDecimal units,
            BigDecimal weightedAvgRate,
            BigDecimal totalCostBase,
            BigDecimal currentRate,
            BigDecimal currentValueBase,
            BigDecimal unrealizedResultBase,
            BigDecimal unrealizedResultPct) {}

    @FunctionalInterface
    public interface CurrentRateLookup {
        Optional<BigDecimal> latestRate(String currencyCode);
    }

    public static Optional<String> resolveRateSource(
            String currencyCode, String referenceRateSource) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return Optional.empty();
        }
        String normalizedCurrency = currencyCode.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedCurrency) {
            case "USD" -> {
                if (referenceRateSource != null) {
                    String normalizedSource = referenceRateSource.trim().toUpperCase(Locale.ROOT);
                    if (normalizedSource.startsWith("USD_")) {
                        yield Optional.of(normalizedSource);
                    }
                }
                yield Optional.of(DEFAULT_USD_INDEX);
            }
            case "EUR" -> Optional.of(DEFAULT_EUR_INDEX);
            default -> Optional.empty();
        };
    }

    public List<PositionSnapshot> calculate(
            String baseCurrency,
            List<ExchangeLeg> chronologicalExchanges,
            CurrentRateLookup rateLookup) {
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(chronologicalExchanges, "chronologicalExchanges must not be null");
        Objects.requireNonNull(rateLookup, "rateLookup must not be null");

        String base = baseCurrency.trim().toUpperCase(Locale.ROOT);
        Map<String, Accumulator> accumulators = new TreeMap<>();

        for (ExchangeLeg leg : chronologicalExchanges) {
            String src = leg.sourceCurrency().trim().toUpperCase(Locale.ROOT);
            String tgt = leg.targetCurrency().trim().toUpperCase(Locale.ROOT);

            if (src.equals(base)) {
                Accumulator targetAcc = accumulators.computeIfAbsent(tgt, k -> new Accumulator());
                targetAcc.addBuy(leg.targetAmount(), leg.sourceAmount());
            } else if (tgt.equals(base)) {
                Accumulator sourceAcc = accumulators.computeIfAbsent(src, k -> new Accumulator());
                sourceAcc.applySell(leg.sourceAmount());
            } else {
                // SWAP: treat as SELL of source currency at its PPP, and BUY of target currency with that cost
                Accumulator sourceAcc = accumulators.computeIfAbsent(src, k -> new Accumulator());
                BigDecimal transferredCost = sourceAcc.applySell(leg.sourceAmount());
                Accumulator targetAcc = accumulators.computeIfAbsent(tgt, k -> new Accumulator());
                targetAcc.addBuy(leg.targetAmount(), transferredCost);
            }
        }

        List<PositionSnapshot> results = new ArrayList<>(accumulators.size());
        for (Map.Entry<String, Accumulator> entry : accumulators.entrySet()) {
            String currencyCode = entry.getKey();
            Accumulator acc = entry.getValue();

            BigDecimal scaledUnits = Money.scale(acc.units);
            BigDecimal scaledCost = Money.scale(acc.cost);
            BigDecimal weightedAvgRate = scaledUnits.compareTo(BigDecimal.ZERO) > 0
                    ? acc.cost.divide(acc.units, Money.RATE_SCALE, RoundingMode.HALF_EVEN)
                    : null;

            BigDecimal currentRate = rateLookup
                    .latestRate(currencyCode)
                    .filter(rate -> rate.compareTo(BigDecimal.ZERO) > 0)
                    .map(Money::rate)
                    .orElse(null);

            BigDecimal currentValueBase =
                    currentRate != null ? Money.scale(scaledUnits.multiply(currentRate)) : null;
            BigDecimal unrealizedResultBase = currentValueBase != null
                    ? Money.scale(currentValueBase.subtract(scaledCost))
                    : null;
            BigDecimal unrealizedResultPct =
                    (unrealizedResultBase != null && scaledCost.compareTo(BigDecimal.ZERO) > 0)
                            ? Money.divide(
                                    unrealizedResultBase.multiply(ONE_HUNDRED), scaledCost)
                            : null;

            results.add(new PositionSnapshot(
                    currencyCode,
                    scaledUnits,
                    weightedAvgRate,
                    scaledCost,
                    currentRate,
                    currentValueBase,
                    unrealizedResultBase,
                    unrealizedResultPct));
        }
        return results;
    }

    private static final class Accumulator {
        private BigDecimal units = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;

        void addBuy(BigDecimal addedUnits, BigDecimal addedCostBase) {
            units = units.add(addedUnits);
            cost = cost.add(addedCostBase);
        }

        BigDecimal applySell(BigDecimal soldUnits) {
            if (units.compareTo(BigDecimal.ZERO) <= 0) {
                units = BigDecimal.ZERO;
                cost = BigDecimal.ZERO;
                return BigDecimal.ZERO;
            }
            BigDecimal remainingUnits = units.subtract(soldUnits);
            if (remainingUnits.compareTo(BigDecimal.ZERO) <= 0) {
                BigDecimal consumedCost = cost;
                units = BigDecimal.ZERO;
                cost = BigDecimal.ZERO;
                return consumedCost;
            }
            BigDecimal avg = cost.divide(units, INTERMEDIATE_SCALE, RoundingMode.HALF_EVEN);
            BigDecimal costDelta =
                    soldUnits.multiply(avg).setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_EVEN);
            units = remainingUnits;
            cost = cost.subtract(costDelta);
            if (cost.compareTo(BigDecimal.ZERO) < 0) {
                cost = BigDecimal.ZERO;
            }
            return costDelta;
        }
    }
}
