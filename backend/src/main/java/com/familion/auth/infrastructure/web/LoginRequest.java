package com.familion.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record LoginRequest(
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Password is required") String password,
        String deviceToken) {

    public LoginRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
