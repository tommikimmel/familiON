package com.familion.analytics.application;

import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsSeries(
        AnalyticsLens lens,
        String unit,
        AnalyticsGranularity granularity,
        List<SeriesPoint> points) {

    public record SeriesPoint(
            LocalDate periodStart,
            String label,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal balance) {}
}
