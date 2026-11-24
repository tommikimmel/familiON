package com.familion.analytics.application;

import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.domain.AnalyticsPeriod;
import com.familion.common.security.View;
import java.math.BigDecimal;

public record AnalyticsSummary(
        AnalyticsPeriod period,
        View view,
        AnalyticsLens lens,
        String unit,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance,
        BigDecimal savingsRatePct,
        BigDecimal installmentCommitmentPct,
        PreviousPeriodTotals previous,
        BigDecimal realVariationPct,
        boolean lensApproximated) {

    public record PreviousPeriodTotals(BigDecimal income, BigDecimal expense) {}
}
