package com.familion.family.infrastructure.web;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record CreateInvitationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 180, message = "Email must be at most 180 characters")
        String email,
        @NotNull(message = "Role is required") Role role,
        FamilyVisibility familyVisibility) {

    public CreateInvitationRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
