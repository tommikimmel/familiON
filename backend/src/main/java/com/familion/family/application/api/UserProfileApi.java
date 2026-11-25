package com.familion.family.application.api;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileApi {

    RegisteredUser registerFamilyWithAdult(
            String email, String rawPassword, String fullName, String familyName);

    UserProfile getProfile(UUID userId);

    UserProfile updatePreferences(UUID userId, String themePreference, String accentColor);

    Optional<AvatarImage> getAvatar(UUID userId);

    /** Validates type (JPEG/PNG/WebP by magic bytes) and size; throws {@link InvalidAvatarException}. */
    UserProfile updateAvatar(UUID userId, byte[] content);

    UserProfile removeAvatar(UUID userId);
}
