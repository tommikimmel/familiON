package com.familion.finance.transactions.application;

public class InvalidTransactionRequestException extends RuntimeException {

    private final String field;

    public InvalidTransactionRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
