package com.familion.auth.infrastructure.web;

import com.familion.auth.application.SecuritySettingsService;
import com.familion.auth.application.SecuritySettingsView;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.CurrentUserProvider;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.UserProfile;
import com.familion.family.application.api.UserProfileApi;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/me")
public class MeController {

    private final CurrentUserProvider currentUserProvider;
    private final UserProfileApi userProfileApi;
    private final FamilyQueryApi familyQueryApi;
    private final SecuritySettingsService securitySettingsService;

    public MeController(
            CurrentUserProvider currentUserProvider,
            UserProfileApi userProfileApi,
            FamilyQueryApi familyQueryApi,
            SecuritySettingsService securitySettingsService) {
        this.currentUserProvider = currentUserProvider;
        this.userProfileApi = userProfileApi;
        this.familyQueryApi = familyQueryApi;
        this.securitySettingsService = securitySettingsService;
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

    // Owner-only by construction: every avatar route acts on the authenticated user's id.
    @GetMapping("/avatar")
    public ResponseEntity<byte[]> avatar() {
        CurrentUser currentUser = currentUserProvider.get();
        return userProfileApi.getAvatar(currentUser.userId())
                .map(a -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(a.contentType()))
                        .header("X-Content-Type-Options", "nosniff")
                        .body(a.content()))
                .orElseThrow(() -> new NotFoundException("Avatar not found"));
    }

    @PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MeResponse uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        CurrentUser currentUser = currentUserProvider.get();
        UserProfile updated = userProfileApi.updateAvatar(currentUser.userId(), file.getBytes());
        return toResponse(updated, familyQueryApi.getSettings(updated.familyId()));
    }

    @DeleteMapping("/avatar")
    public MeResponse removeAvatar() {
        CurrentUser currentUser = currentUserProvider.get();
        UserProfile updated = userProfileApi.removeAvatar(currentUser.userId());
        return toResponse(updated, familyQueryApi.getSettings(updated.familyId()));
    }

    @GetMapping("/security")
    public SecuritySettingsResponse securitySettings() {
        CurrentUser currentUser = currentUserProvider.get();
        return toSecurityResponse(securitySettingsService.getSettings(currentUser.userId()));
    }

    @PatchMapping("/security")
    public SecuritySettingsResponse updateSecuritySettings(
            @Valid @RequestBody UpdateSecuritySettingsRequest request) {
        CurrentUser currentUser = currentUserProvider.get();
        return toSecurityResponse(securitySettingsService.updateTwoFactorEnabled(
                currentUser.userId(), request.twoFactorEnabled()));
    }

    @DeleteMapping("/security/trusted-devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeTrustedDevice(@PathVariable UUID id) {
        CurrentUser currentUser = currentUserProvider.get();
        securitySettingsService.revokeTrustedDevice(currentUser.userId(), id);
    }

    private static SecuritySettingsResponse toSecurityResponse(SecuritySettingsView view) {
        List<TrustedDeviceResponse> devices = view.trustedDevices().stream()
                .map(d -> new TrustedDeviceResponse(
                        d.id(), d.deviceName(), d.createdAt(), d.expiresAt()))
                .toList();
        return new SecuritySettingsResponse(view.twoFactorEnabled(), devices);
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
                profile.avatarUpdatedAt(),
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
