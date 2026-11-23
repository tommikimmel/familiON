package com.familion.family.domain;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.persistence.AuditableEntity;
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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    public static final String DEFAULT_ACCENT_COLOR = "#0F766E";
    public static final Pattern ACCENT_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "email", nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "family_visibility", nullable = false, length = 20)
    private FamilyVisibility familyVisibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_preference", nullable = false, length = 10)
    private ThemePreference themePreference;

    @Column(name = "accent_color", nullable = false, length = 7)
    private String accentColor;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected User() {}

    private User(
            UUID familyId,
            String email,
            String passwordHash,
            String fullName,
            Role role,
            FamilyVisibility familyVisibility,
            Instant emailVerifiedAt,
            boolean twoFactorEnabled) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.email = normalizeEmail(Objects.requireNonNull(email, "email must not be null"));
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.familyVisibility =
                Objects.requireNonNull(familyVisibility, "familyVisibility must not be null");
        if (this.role == Role.ADULTO && this.familyVisibility != FamilyVisibility.FAMILY_FULL) {
            throw new BusinessRuleException("Adult members must have FAMILY_FULL visibility");
        }
        this.themePreference = ThemePreference.SYSTEM;
        this.accentColor = DEFAULT_ACCENT_COLOR;
        this.emailVerifiedAt = emailVerifiedAt;
        this.twoFactorEnabled = twoFactorEnabled;
        this.active = true;
    }

    public static User createMember(
            UUID familyId,
            String email,
            String passwordHash,
            String fullName,
            Role role,
            FamilyVisibility familyVisibility,
            Instant emailVerifiedAt) {
        return new User(
                familyId,
                email,
                passwordHash,
                fullName,
                role,
                familyVisibility,
                emailVerifiedAt,
                true);
    }

    public static String normalizeEmail(String email) {
        Objects.requireNonNull(email, "email must not be null");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void markEmailVerified(Instant verifiedAt) {
        Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
        if (this.emailVerifiedAt == null) {
            this.emailVerifiedAt = verifiedAt;
        }
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash =
                Objects.requireNonNull(newPasswordHash, "newPasswordHash must not be null");
    }

    public void setTwoFactorEnabled(boolean twoFactorEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
    }

    public void updatePreferences(ThemePreference themePreference, String accentColor) {
        if (themePreference != null) {
            this.themePreference = themePreference;
        }
        if (accentColor != null) {
            if (!ACCENT_COLOR_PATTERN.matcher(accentColor).matches()) {
                throw new BusinessRuleException("Accent color must match #RRGGBB format");
            }
            this.accentColor = accentColor;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public FamilyVisibility getFamilyVisibility() {
        return familyVisibility;
    }

    public ThemePreference getThemePreference() {
        return themePreference;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public boolean isActive() {
        return active;
    }
}
