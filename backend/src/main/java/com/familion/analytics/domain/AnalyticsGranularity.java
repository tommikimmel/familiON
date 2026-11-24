package com.familion.analytics.domain;

public enum AnalyticsGranularity {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    private final String sqlDateTruncUnit;

    AnalyticsGranularity(String sqlDateTruncUnit) {
        this.sqlDateTruncUnit = sqlDateTruncUnit;
    }

    public String sqlDateTruncUnit() {
        return sqlDateTruncUnit;
    }
}
