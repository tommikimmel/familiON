package com.familion.auth.infrastructure.web;

import java.util.List;

public record SecuritySettingsResponse(
        boolean twoFactorEnabled, List<TrustedDeviceResponse> trustedDevices) {}
