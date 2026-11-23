package com.familion.auth.infrastructure.web;

import jakarta.validation.constraints.NotNull;

public record UpdateSecuritySettingsRequest(
        @NotNull(message = "twoFactorEnabled is required") Boolean twoFactorEnabled) {}
