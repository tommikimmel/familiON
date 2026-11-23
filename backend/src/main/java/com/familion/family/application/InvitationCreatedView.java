package com.familion.family.application;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import java.time.Instant;
import java.util.UUID;

public record InvitationCreatedView(
        UUID id,
        String email,
        Role role,
        FamilyVisibility familyVisibility,
        Instant expiresAt,
        Instant createdAt,
        InviterView invitedBy,
        String inviteUrl) {}
