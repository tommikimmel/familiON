package com.familion.family.allowances.application;

public class InvalidAllowanceRequestException extends RuntimeException {

    private final String field;

    public InvalidAllowanceRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
