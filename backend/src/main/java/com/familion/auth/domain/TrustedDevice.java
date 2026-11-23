package com.familion.auth.domain;

import com.familion.common.persistence.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "trusted_devices")
public class TrustedDevice extends CreatedAtEntity {

    public static final int MAX_DEVICE_NAME_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "device_token_hash", nullable = false, unique = true, length = 255)
    private String deviceTokenHash;

    @Column(name = "device_name", length = MAX_DEVICE_NAME_LENGTH)
    private String deviceName;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected TrustedDevice() {}

    private TrustedDevice(
            UUID userId, String deviceTokenHash, String deviceName, Instant expiresAt) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.deviceTokenHash =
                Objects.requireNonNull(deviceTokenHash, "deviceTokenHash must not be null");
        this.deviceName = truncateDeviceName(deviceName);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static TrustedDevice create(
            UUID userId, String deviceTokenHash, String deviceName, Instant expiresAt) {
        return new TrustedDevice(userId, deviceTokenHash, deviceName, expiresAt);
    }

    public static String truncateDeviceName(String rawDeviceName) {
        if (rawDeviceName == null || rawDeviceName.isBlank()) {
            return null;
        }
        String trimmed = rawDeviceName.trim();
        if (trimmed.length() <= MAX_DEVICE_NAME_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_DEVICE_NAME_LENGTH);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !expiresAt.isAfter(now);
    }

    public boolean isActive(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    public void revoke(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDeviceTokenHash() {
        return deviceTokenHash;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
