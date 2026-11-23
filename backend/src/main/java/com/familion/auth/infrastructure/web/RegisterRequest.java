package com.familion.auth.infrastructure.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 180, message = "Email must be at most 180 characters")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
        @Pattern(
                regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
                message = "Password must contain at least one letter and one number")
        String password,
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,
        @NotBlank(message = "Family name is required")
        @Size(max = 120, message = "Family name must be at most 120 characters")
        String familyName) {

    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        fullName = fullName == null ? null : fullName.trim();
        familyName = familyName == null ? null : familyName.trim();
    }
}
