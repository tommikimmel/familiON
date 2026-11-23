package com.familion.common.security;

import java.util.UUID;

public record CurrentUser(UUID userId, UUID familyId, Role role, FamilyVisibility visibility) {

    public boolean isAdult() {
        return role == Role.ADULTO;
    }
}
