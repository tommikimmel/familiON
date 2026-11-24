package com.familion.finance.recurring.application;

public class InvalidRecurringExpenseRequestException extends RuntimeException {

    private final String field;

    public InvalidRecurringExpenseRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
