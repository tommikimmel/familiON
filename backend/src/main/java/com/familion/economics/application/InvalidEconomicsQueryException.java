package com.familion.economics.application;

public class InvalidEconomicsQueryException extends RuntimeException {

    private final String field;

    public InvalidEconomicsQueryException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
