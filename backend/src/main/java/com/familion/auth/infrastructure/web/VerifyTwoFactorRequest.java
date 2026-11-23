package com.familion.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyTwoFactorRequest(
        @NotBlank(message = "Challenge token is required") String challengeToken,
        @NotBlank(message = "Code is required")
        @Size(max = 64, message = "Code is invalid")
        String code,
        boolean trustDevice,
        @Size(max = 150, message = "Device name must be at most 150 characters")
        String deviceName) {

    public VerifyTwoFactorRequest {
        challengeToken = challengeToken == null ? null : challengeToken.trim();
        code = code == null ? null : code.trim();
        deviceName = deviceName == null ? null : deviceName.trim();
    }
}
