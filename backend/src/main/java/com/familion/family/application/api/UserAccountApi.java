package com.familion.family.application.api;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountApi {

    Optional<UserAccount> findByEmail(String email);

    UserAccount require(UUID userId);

    UUID createMember(
            UUID familyId,
            String email,
            String fullName,
            String rawPassword,
            Role role,
            FamilyVisibility visibility,
            boolean emailVerified);

    void markEmailVerified(UUID userId);

    void changePassword(UUID userId, String rawPassword);

    void setTwoFactorEnabled(UUID userId, boolean enabled);
}
