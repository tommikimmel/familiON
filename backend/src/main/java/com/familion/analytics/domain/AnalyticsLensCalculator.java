package com.familion.analytics.domain;

import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public final class AnalyticsLensCalculator {

    public static final BigDecimal ZERO = Money.scale(BigDecimal.ZERO);
    public static final BigDecimal ONE_HUNDRED = Money.scale(new BigDecimal("100"));
    private static final int INTERMEDIATE_SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public String unit(AnalyticsLens lens, String baseCurrency, String referenceCurrency) {
        Objects.requireNonNull(lens, "lens must not be null");
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(referenceCurrency, "referenceCurrency must not be null");

        return switch (lens) {
            case NOMINAL -> baseCurrency;
            case CONSTANT_BASE -> baseCurrency + " (hoy)";
            case REFERENCE_CURRENCY -> referenceCurrency;
            case INCOME_PCT -> "%";
        };
    }

    public ConstantBaseValue constantBaseAmount(
            BigDecimal baseCurrencyAmount, BigDecimal snapshotIpcIndex, BigDecimal latestIpcIndex) {
        Objects.requireNonNull(baseCurrencyAmount, "baseCurrencyAmount must not be null");
        if (isPositive(latestIpcIndex) && isPositive(snapshotIpcIndex)) {
            BigDecimal adjusted =
                    Money.divide(baseCurrencyAmount.multiply(latestIpcIndex), snapshotIpcIndex);
            return new ConstantBaseValue(adjusted, false);
        }
        return new ConstantBaseValue(Money.scale(baseCurrencyAmount), true);
    }

    public BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || !isPositive(denominator)) {
            return null;
        }
        return Money.divide(numerator.multiply(ONE_HUNDRED), denominator);
    }

    public BigDecimal incomePctAmount(BigDecimal nominalAmount, BigDecimal nominalIncome) {
        if (!isPositive(nominalIncome)) {
            return null;
        }
        BigDecimal safeAmount = nominalAmount == null ? ZERO : nominalAmount;
        return Money.divide(safeAmount.multiply(ONE_HUNDRED), nominalIncome);
    }

    public BigDecimal savingsRatePct(BigDecimal income, BigDecimal balance) {
        if (balance == null || !isPositive(income)) {
            return null;
        }
        return Money.divide(balance.multiply(ONE_HUNDRED), income);
    }

    public BigDecimal variationPct(BigDecimal current, BigDecimal baseline) {
        if (current == null || !isPositive(baseline)) {
            return null;
        }
        return Money.divide(current.subtract(baseline).multiply(ONE_HUNDRED), baseline);
    }

    public BigDecimal realVariationPct(
            BigDecimal currentRealExpense, List<BigDecimal> previousRealExpenses) {
        if (currentRealExpense == null || previousRealExpenses == null || previousRealExpenses.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int countWithHistory = 0;
        for (BigDecimal expense : previousRealExpenses) {
            if (isPositive(expense)) {
                sum = sum.add(expense);
                countWithHistory++;
            }
        }
        if (countWithHistory == 0) {
            return null;
        }
        BigDecimal average =
                sum.divide(BigDecimal.valueOf(countWithHistory), INTERMEDIATE_SCALE, ROUNDING);
        return variationPct(currentRealExpense, average);
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public record ConstantBaseValue(BigDecimal amount, boolean approximated) {}
}
