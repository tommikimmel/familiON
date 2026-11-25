package com.familion.analytics.exports.domain;

public class InvalidExportRequestException extends RuntimeException {

    private final String field;

    public InvalidExportRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
