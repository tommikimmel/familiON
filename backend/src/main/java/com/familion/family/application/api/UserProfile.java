package com.familion.family.application.api;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import java.util.UUID;

public record UserProfile(
        UUID id,
        UUID familyId,
        String email,
        String fullName,
        Role role,
        FamilyVisibility familyVisibility,
        String themePreference,
        String accentColor,
        boolean twoFactorEnabled,
        boolean emailVerified) {}
