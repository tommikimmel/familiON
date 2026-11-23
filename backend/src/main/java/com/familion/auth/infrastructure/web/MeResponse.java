package com.familion.auth.infrastructure.web;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String fullName,
        Role role,
        FamilyVisibility familyVisibility,
        String themePreference,
        String accentColor,
        boolean twoFactorEnabled,
        boolean emailVerified,
        MeFamilyResponse family) {}
