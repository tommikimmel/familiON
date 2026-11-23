package com.familion.family.application.api;

import java.util.UUID;

public interface UserProfileApi {

    RegisteredUser registerFamilyWithAdult(
            String email, String rawPassword, String fullName, String familyName);

    UserProfile getProfile(UUID userId);

    UserProfile updatePreferences(UUID userId, String themePreference, String accentColor);
}
