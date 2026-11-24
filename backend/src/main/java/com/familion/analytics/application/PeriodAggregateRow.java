package com.familion.analytics.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PeriodAggregateRow(
        LocalDate periodStart,
        BigDecimal lensIncome,
        BigDecimal lensExpense,
        BigDecimal nominalIncome,
        BigDecimal nominalExpense,
        BigDecimal constantBaseExpense,
        boolean lensApproximated) {

    public static PeriodAggregateRow empty(LocalDate periodStart) {
        return new PeriodAggregateRow(
                periodStart,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false);
    }
}
