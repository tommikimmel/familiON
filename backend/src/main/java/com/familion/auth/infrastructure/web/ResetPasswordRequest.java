package com.familion.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 255, message = "Token is invalid")
        String token,
        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
        @Pattern(
                regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
                message = "Password must contain at least one letter and one number")
        String newPassword) {

    public ResetPasswordRequest {
        token = token == null ? null : token.trim();
    }
}
