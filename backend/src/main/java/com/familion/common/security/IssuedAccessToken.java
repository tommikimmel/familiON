package com.familion.common.security;

import java.time.Instant;
import java.util.UUID;

public record IssuedAccessToken(String token, UUID jti, Instant expiresAt, long expiresInSeconds) {}
