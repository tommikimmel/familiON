package com.familion.finance.accounts.application;

public class InvalidAccountRequestException extends RuntimeException {

    private final String field;

    public InvalidAccountRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
