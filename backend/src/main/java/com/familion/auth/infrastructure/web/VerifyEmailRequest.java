package com.familion.auth.infrastructure.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record VerifyEmailRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 180, message = "Email must be at most 180 characters")
        String email,
        @NotBlank(message = "Code is required")
        @Size(max = 64, message = "Code is invalid")
        String code) {

    public VerifyEmailRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        code = code == null ? null : code.trim();
    }
}
