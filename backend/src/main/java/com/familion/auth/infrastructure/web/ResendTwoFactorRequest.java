package com.familion.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record ResendTwoFactorRequest(
        @NotBlank(message = "Challenge token is required") String challengeToken) {

    public ResendTwoFactorRequest {
        challengeToken = challengeToken == null ? null : challengeToken.trim();
    }
}
