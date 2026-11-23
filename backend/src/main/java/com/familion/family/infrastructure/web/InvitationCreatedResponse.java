package com.familion.family.infrastructure.web;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.family.application.InvitationCreatedView;
import java.time.Instant;
import java.util.UUID;

public record InvitationCreatedResponse(
        UUID id,
        String email,
        Role role,
        FamilyVisibility familyVisibility,
        Instant expiresAt,
        Instant createdAt,
        InviterResponse invitedBy,
        String inviteUrl) {

    public static InvitationCreatedResponse from(InvitationCreatedView view) {
        return new InvitationCreatedResponse(
                view.id(),
                view.email(),
                view.role(),
                view.familyVisibility(),
                view.expiresAt(),
                view.createdAt(),
                InviterResponse.from(view.invitedBy()),
                view.inviteUrl());
    }
}
