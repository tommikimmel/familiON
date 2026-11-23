package com.familion.finance.transactions.domain;

import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.util.Objects;

public final class LensCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String PERCENT_UNIT = "%";

    public LensResult calculate(
            Lens lens,
            String baseCurrency,
            String referenceCurrency,
            BigDecimal rawAmount,
            BigDecimal baseCurrencyAmount,
            BigDecimal amountInReference,
            String snapshotReferenceCurrency,
            BigDecimal snapshotIpcIndex,
            BigDecimal latestIpcIndex,
            BigDecimal monthlyIncomeBase) {
        Objects.requireNonNull(lens, "lens must not be null");
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(referenceCurrency, "referenceCurrency must not be null");
        Objects.requireNonNull(rawAmount, "rawAmount must not be null");

        return switch (lens) {
            case NOMINAL -> calculateNominal(baseCurrency, rawAmount, baseCurrencyAmount);
            case CONSTANT_BASE -> calculateConstantBase(
                    baseCurrency, rawAmount, baseCurrencyAmount, snapshotIpcIndex, latestIpcIndex);
            case REFERENCE_CURRENCY -> calculateReference(
                    referenceCurrency, rawAmount, amountInReference, snapshotReferenceCurrency);
            case INCOME_PCT -> calculateIncomePct(rawAmount, baseCurrencyAmount, monthlyIncomeBase);
        };
    }

    private static LensResult calculateNominal(
            String baseCurrency, BigDecimal rawAmount, BigDecimal baseCurrencyAmount) {
        if (baseCurrencyAmount != null) {
            return new LensResult(Lens.NOMINAL, Money.scale(baseCurrencyAmount), baseCurrency, false);
        }
        return new LensResult(Lens.NOMINAL, Money.scale(rawAmount), baseCurrency, true);
    }

    private static LensResult calculateConstantBase(
            String baseCurrency,
            BigDecimal rawAmount,
            BigDecimal baseCurrencyAmount,
            BigDecimal snapshotIpcIndex,
            BigDecimal latestIpcIndex) {
        if (baseCurrencyAmount != null
                && isPositive(latestIpcIndex)
                && isPositive(snapshotIpcIndex)) {
            BigDecimal adjusted =
                    Money.divide(baseCurrencyAmount.multiply(latestIpcIndex), snapshotIpcIndex);
            return new LensResult(Lens.CONSTANT_BASE, adjusted, baseCurrency, false);
        }
        BigDecimal fallback =
                baseCurrencyAmount != null ? Money.scale(baseCurrencyAmount) : Money.scale(rawAmount);
        return new LensResult(Lens.CONSTANT_BASE, fallback, baseCurrency, true);
    }

    private static LensResult calculateReference(
            String referenceCurrency,
            BigDecimal rawAmount,
            BigDecimal amountInReference,
            String snapshotReferenceCurrency) {
        String unit = snapshotReferenceCurrency != null ? snapshotReferenceCurrency : referenceCurrency;
        if (amountInReference != null) {
            return new LensResult(
                    Lens.REFERENCE_CURRENCY, Money.scale(amountInReference), unit, false);
        }
        return new LensResult(Lens.REFERENCE_CURRENCY, Money.scale(rawAmount), unit, true);
    }

    private static LensResult calculateIncomePct(
            BigDecimal rawAmount, BigDecimal baseCurrencyAmount, BigDecimal monthlyIncomeBase) {
        if (!isPositive(monthlyIncomeBase)) {
            return new LensResult(Lens.INCOME_PCT, null, PERCENT_UNIT, false);
        }
        if (baseCurrencyAmount == null) {
            BigDecimal pct = Money.divide(rawAmount.multiply(ONE_HUNDRED), monthlyIncomeBase);
            return new LensResult(Lens.INCOME_PCT, pct, PERCENT_UNIT, true);
        }
        BigDecimal pct = Money.divide(baseCurrencyAmount.multiply(ONE_HUNDRED), monthlyIncomeBase);
        return new LensResult(Lens.INCOME_PCT, pct, PERCENT_UNIT, false);
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
