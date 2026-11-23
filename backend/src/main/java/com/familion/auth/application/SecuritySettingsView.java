package com.familion.auth.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SecuritySettingsView(
        boolean twoFactorEnabled, List<TrustedDeviceView> trustedDevices) {

    public record TrustedDeviceView(
            UUID id, String deviceName, Instant createdAt, Instant expiresAt) {}
}
