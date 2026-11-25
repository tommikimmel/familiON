package com.familion.analytics.exports.application;

import java.math.BigDecimal;

public record MonthlySummaryRow(
        String label,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance,
        BigDecimal savingsRatePct,
        boolean totalRow) {}
