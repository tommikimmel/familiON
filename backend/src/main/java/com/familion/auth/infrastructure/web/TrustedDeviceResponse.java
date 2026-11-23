package com.familion.auth.infrastructure.web;

import java.time.Instant;
import java.util.UUID;

public record TrustedDeviceResponse(
        UUID id, String deviceName, Instant createdAt, Instant expiresAt) {}
