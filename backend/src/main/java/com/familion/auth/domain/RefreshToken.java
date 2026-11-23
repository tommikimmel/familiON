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
@Table(name = "refresh_tokens")
public class RefreshToken extends CreatedAtEntity {

    public static final int MAX_DEVICE_INFO_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    protected RefreshToken() {}

    private RefreshToken(UUID userId, String tokenHash, Instant expiresAt, String deviceInfo) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.deviceInfo = truncateDeviceInfo(deviceInfo);
    }

    public static RefreshToken create(
            UUID userId, String tokenHash, Instant expiresAt, String deviceInfo) {
        return new RefreshToken(userId, tokenHash, expiresAt, deviceInfo);
    }

    public static String truncateDeviceInfo(String rawDeviceInfo) {
        if (rawDeviceInfo == null || rawDeviceInfo.isBlank()) {
            return null;
        }
        String trimmed = rawDeviceInfo.trim();
        if (trimmed.length() <= MAX_DEVICE_INFO_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_DEVICE_INFO_LENGTH);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
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

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }
}
