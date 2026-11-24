package com.familion.analytics.application;

import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.domain.AnalyticsPeriod;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PersonBreakdown(
        AnalyticsPeriod period,
        AnalyticsLens lens,
        String unit,
        BigDecimal total,
        List<PersonSlice> slices) {

    public record PersonSlice(
            UUID userId,
            String fullName,
            String accentColor,
            BigDecimal amount,
            BigDecimal pct) {}
}
