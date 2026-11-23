package com.familion.family.domain;

import com.familion.common.persistence.CreatedAtEntity;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
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
@Table(name = "family_invitations")
public class FamilyInvitation extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "invited_by_user_id", nullable = false, updatable = false)
    private UUID invitedByUserId;

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "family_visibility", nullable = false, length = 20)
    private FamilyVisibility familyVisibility;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected FamilyInvitation() {}

    private FamilyInvitation(
            UUID familyId,
            UUID invitedByUserId,
            String email,
            Role role,
            FamilyVisibility familyVisibility,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.invitedByUserId =
                Objects.requireNonNull(invitedByUserId, "invitedByUserId must not be null");
        this.email = User.normalizeEmail(Objects.requireNonNull(email, "email must not be null"));
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.familyVisibility = resolveVisibility(role, familyVisibility);
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        setCreatedAt(Objects.requireNonNull(createdAt, "createdAt must not be null"));
    }

    public static FamilyInvitation create(
            UUID familyId,
            UUID invitedByUserId,
            String email,
            Role role,
            FamilyVisibility requestedVisibility,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt) {
        return new FamilyInvitation(
                familyId,
                invitedByUserId,
                email,
                role,
                requestedVisibility,
                tokenHash,
                createdAt,
                expiresAt);
    }

    public static FamilyVisibility resolveVisibility(
            Role role, FamilyVisibility requestedVisibility) {
        Objects.requireNonNull(role, "role must not be null");
        if (role == Role.ADULTO) {
            return FamilyVisibility.FAMILY_FULL;
        }
        return requestedVisibility != null ? requestedVisibility : FamilyVisibility.OWN_ONLY;
    }

    public boolean isAccepted() {
        return acceptedAt != null;
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !expiresAt.isAfter(now);
    }

    public boolean isRedeemable(Instant now) {
        return !isAccepted() && !isExpired(now);
    }

    public void markAccepted(Instant acceptedAt) {
        this.acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public FamilyVisibility getFamilyVisibility() {
        return familyVisibility;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
