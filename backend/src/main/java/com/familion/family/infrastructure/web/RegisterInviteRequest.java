package com.familion.family.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterInviteRequest(
        @NotBlank(message = "Token is required") String token,
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,
        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
        @Pattern(
                regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
                message = "Password must contain at least one letter and one number")
        String password) {

    public RegisterInviteRequest {
        token = token == null ? null : token.trim();
        fullName = fullName == null ? null : fullName.trim();
    }
}
