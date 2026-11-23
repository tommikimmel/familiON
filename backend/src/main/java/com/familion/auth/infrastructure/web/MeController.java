package com.familion.auth.infrastructure.web;

import com.familion.common.security.CurrentUser;
import com.familion.common.security.CurrentUserProvider;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.UserProfile;
import com.familion.family.application.api.UserProfileApi;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/me")
public class MeController {

    private final CurrentUserProvider currentUserProvider;
    private final UserProfileApi userProfileApi;
    private final FamilyQueryApi familyQueryApi;

    public MeController(
            CurrentUserProvider currentUserProvider,
            UserProfileApi userProfileApi,
            FamilyQueryApi familyQueryApi) {
        this.currentUserProvider = currentUserProvider;
        this.userProfileApi = userProfileApi;
        this.familyQueryApi = familyQueryApi;
    }

    @GetMapping
    public MeResponse me() {
        CurrentUser currentUser = currentUserProvider.get();
        UserProfile profile = userProfileApi.getProfile(currentUser.userId());
        FamilySettings family = familyQueryApi.getSettings(profile.familyId());
        return toResponse(profile, family);
    }

    @PatchMapping("/preferences")
    public MeResponse updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
        CurrentUser currentUser = currentUserProvider.get();
        String themePreference =
                request.themePreference() != null ? request.themePreference().name() : null;
        UserProfile updated = userProfileApi.updatePreferences(
                currentUser.userId(), themePreference, request.accentColor());
        FamilySettings family = familyQueryApi.getSettings(updated.familyId());
        return toResponse(updated, family);
    }

    private static MeResponse toResponse(UserProfile profile, FamilySettings family) {
        return new MeResponse(
                profile.id(),
                profile.email(),
                profile.fullName(),
                profile.role(),
                profile.familyVisibility(),
                profile.themePreference(),
                profile.accentColor(),
                profile.twoFactorEnabled(),
                profile.emailVerified(),
                new MeFamilyResponse(
                        family.familyId(),
                        family.name(),
                        family.baseCurrency(),
                        family.referenceCurrency(),
                        family.referenceRateSource(),
                        family.timezone().getId(),
                        family.enabledCurrencies()));
    }
}
