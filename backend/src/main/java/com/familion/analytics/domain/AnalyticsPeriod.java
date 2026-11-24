package com.familion.analytics.domain;

import java.time.LocalDate;

public record AnalyticsPeriod(
        AnalyticsGranularity granularity, LocalDate from, LocalDate to, String label) {}
