package com.familion.family.infrastructure.web;

import com.familion.common.security.Role;
import com.familion.family.application.InvitationPreview;
import java.time.Instant;

public record InvitationPreviewResponse(
        String email, Role role, String familyName, Instant expiresAt) {

    public static InvitationPreviewResponse from(InvitationPreview preview) {
        return new InvitationPreviewResponse(
                preview.email(), preview.role(), preview.familyName(), preview.expiresAt());
    }
}
