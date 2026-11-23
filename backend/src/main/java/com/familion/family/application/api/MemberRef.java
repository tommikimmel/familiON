package com.familion.family.application.api;

import com.familion.common.security.Role;
import java.util.UUID;

public record MemberRef(UUID userId, UUID familyId, String fullName, Role role, String accentColor) {}
