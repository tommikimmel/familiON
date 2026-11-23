package com.familion.family.application;

import com.familion.common.security.Role;
import java.time.Instant;

public record InvitationPreview(
        String email, Role role, String familyName, Instant expiresAt) {}
