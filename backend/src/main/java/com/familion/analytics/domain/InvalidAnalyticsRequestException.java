package com.familion.analytics.domain;

public class InvalidAnalyticsRequestException extends RuntimeException {

    private final String field;

    public InvalidAnalyticsRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
