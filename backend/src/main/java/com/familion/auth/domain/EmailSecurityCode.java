package com.familion.auth.domain;

import com.familion.common.persistence.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "email_security_codes")
public class EmailSecurityCode extends CreatedAtEntity {

    public static final int MAX_REQUEST_IP_LENGTH = 45;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 25)
    private EmailCodePurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "max_attempts", nullable = false)
    private short maxAttempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "request_ip", length = MAX_REQUEST_IP_LENGTH)
    private String requestIp;

    protected EmailSecurityCode() {}

    private EmailSecurityCode(
            UUID userId,
            EmailCodePurpose purpose,
            String codeHash,
            short maxAttempts,
            Instant expiresAt,
            String requestIp) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash must not be null");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.attempts = 0;
        this.maxAttempts = maxAttempts;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.requestIp = truncateIp(requestIp);
    }

    public static EmailSecurityCode create(
            UUID userId,
            EmailCodePurpose purpose,
            String codeHash,
            short maxAttempts,
            Instant expiresAt,
            String requestIp) {
        return new EmailSecurityCode(userId, purpose, codeHash, maxAttempts, expiresAt, requestIp);
    }

    public static String truncateIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return null;
        }
        String trimmed = rawIp.trim();
        if (trimmed.length() <= MAX_REQUEST_IP_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_REQUEST_IP_LENGTH);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !expiresAt.isAfter(now);
    }

    public boolean hasExhaustedAttempts() {
        return attempts >= maxAttempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public int remainingAttempts() {
        return Math.max(0, maxAttempts - attempts);
    }

    public void consume(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (this.consumedAt == null) {
            this.consumedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public EmailCodePurpose getPurpose() {
        return purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public short getAttempts() {
        return attempts;
    }

    public short getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public String getRequestIp() {
        return requestIp;
    }
}
