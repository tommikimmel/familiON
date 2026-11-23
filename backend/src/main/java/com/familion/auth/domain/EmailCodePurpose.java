package com.familion.auth.domain;

import java.time.Duration;

public enum EmailCodePurpose {
    TWO_FACTOR_LOGIN(Duration.ofMinutes(10), (short) 5, false),
    EMAIL_VERIFICATION(Duration.ofHours(24), (short) 5, false),
    PASSWORD_RESET(Duration.ofMinutes(20), (short) 5, true);

    private final Duration ttl;
    private final short maxAttempts;
    private final boolean longToken;

    EmailCodePurpose(Duration ttl, short maxAttempts, boolean longToken) {
        this.ttl = ttl;
        this.maxAttempts = maxAttempts;
        this.longToken = longToken;
    }

    public Duration ttl() {
        return ttl;
    }

    public short maxAttempts() {
        return maxAttempts;
    }

    public boolean isLongToken() {
        return longToken;
    }
}
