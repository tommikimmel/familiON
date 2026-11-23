package com.familion.family.application;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;

public record CreateInvitationCommand(
        String email, Role role, FamilyVisibility familyVisibility) {}
