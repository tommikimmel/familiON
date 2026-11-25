package com.familion.family.application.api;

public class InvalidAvatarException extends RuntimeException {

    public enum Reason { EMPTY, TOO_LARGE, UNSUPPORTED_TYPE }

    private final Reason reason;

    public InvalidAvatarException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
