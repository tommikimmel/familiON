package com.familion.family.application;

public class InvalidFamilyRequestException extends RuntimeException {

    private final String field;

    public InvalidFamilyRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
