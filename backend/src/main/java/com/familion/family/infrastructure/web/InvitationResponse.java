package com.familion.family.infrastructure.web;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.family.application.InvitationView;
import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        String email,
        Role role,
        FamilyVisibility familyVisibility,
        Instant expiresAt,
        Instant createdAt,
        InviterResponse invitedBy) {

    public static InvitationResponse from(InvitationView view) {
        return new InvitationResponse(
                view.id(),
                view.email(),
                view.role(),
                view.familyVisibility(),
                view.expiresAt(),
                view.createdAt(),
                InviterResponse.from(view.invitedBy()));
    }
}
