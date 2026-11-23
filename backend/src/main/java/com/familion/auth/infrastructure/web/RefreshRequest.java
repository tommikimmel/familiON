package com.familion.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Refresh token is required") String refreshToken) {}
