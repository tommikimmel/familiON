package com.familion.family.application.api;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import java.util.UUID;

public record UserAccount(
        UUID id,
        UUID familyId,
        String email,
        String fullName,
        Role role,
        FamilyVisibility visibility,
        String passwordHash,
        boolean emailVerified,
        boolean twoFactorEnabled,
        boolean active) {}
