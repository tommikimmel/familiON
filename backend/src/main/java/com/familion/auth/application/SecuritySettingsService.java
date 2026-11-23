package com.familion.auth.application;

import com.familion.auth.domain.TrustedDevice;
import com.familion.auth.infrastructure.persistence.TrustedDeviceRepository;
import com.familion.common.error.NotFoundException;
import com.familion.family.application.api.UserAccount;
import com.familion.family.application.api.UserAccountApi;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecuritySettingsService {

    private final UserAccountApi userAccountApi;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final Clock clock;

    public SecuritySettingsService(
            UserAccountApi userAccountApi,
            TrustedDeviceRepository trustedDeviceRepository,
            Clock clock) {
        this.userAccountApi = userAccountApi;
        this.trustedDeviceRepository = trustedDeviceRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SecuritySettingsView getSettings(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        UserAccount user = userAccountApi.require(userId);
        List<SecuritySettingsView.TrustedDeviceView> devices = trustedDeviceRepository
                .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        userId, clock.instant())
                .stream()
                .map(device -> new SecuritySettingsView.TrustedDeviceView(
                        device.getId(),
                        device.getDeviceName(),
                        device.getCreatedAt(),
                        device.getExpiresAt()))
                .toList();
        return new SecuritySettingsView(user.twoFactorEnabled(), devices);
    }

    @Transactional
    public SecuritySettingsView updateTwoFactorEnabled(UUID userId, boolean enabled) {
        Objects.requireNonNull(userId, "userId must not be null");
        userAccountApi.setTwoFactorEnabled(userId, enabled);
        return getSettings(userId);
    }

    @Transactional
    public void revokeTrustedDevice(UUID userId, UUID deviceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        TrustedDevice device = trustedDeviceRepository
                .findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new NotFoundException("Trusted device not found"));
        if (!device.isRevoked()) {
            device.revoke(clock.instant());
            trustedDeviceRepository.save(device);
        }
    }

    @Transactional
    public void revokeAllTrustedDevices(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        trustedDeviceRepository.revokeAllActiveByUserId(userId, clock.instant());
    }
}
