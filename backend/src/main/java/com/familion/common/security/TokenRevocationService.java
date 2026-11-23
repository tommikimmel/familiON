package com.familion.common.security;

import java.time.Instant;
import java.util.UUID;

public interface TokenRevocationService {

    void revoke(UUID jti, Instant expiresAt);

    boolean isRevoked(UUID jti);
}
