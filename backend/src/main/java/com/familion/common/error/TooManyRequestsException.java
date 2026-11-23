package com.familion.common.error;

import java.time.Duration;
import java.util.Objects;

public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String msg, Duration retryAfter) {
        super(msg);
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter must not be null");
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
