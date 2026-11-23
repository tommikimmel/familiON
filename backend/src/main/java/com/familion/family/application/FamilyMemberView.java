package com.familion.family.application;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import java.util.UUID;

public record FamilyMemberView(
        UUID id,
        String fullName,
        String email,
        Role role,
        FamilyVisibility familyVisibility,
        String accentColor,
        boolean active) {}
