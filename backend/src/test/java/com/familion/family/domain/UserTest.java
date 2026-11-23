package com.familion.family.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ConflictException;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void normalizesEmailByTrimmingAndLowercasing() {
        assertThat(User.normalizeEmail("  Ana.Perez@Example.COM ")).isEqualTo("ana.perez@example.com");
    }

    @Test
    void rejectsAdultWithoutFamilyFullVisibility() {
        assertThatThrownBy(() -> User.createMember(
                        UUID.randomUUID(),
                        "adult@familion.test",
                        "{bcrypt}hash",
                        "Adult User",
                        Role.ADULTO,
                        FamilyVisibility.OWN_ONLY,
                        null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void updatesPreferencesAndRejectsInvalidAccentColor() {
        User user = User.createMember(
                UUID.randomUUID(),
                "user@familion.test",
                "{bcrypt}hash",
                "User",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL,
                null);

        user.updatePreferences(ThemePreference.DARK, "#1A2B3C");
        assertThat(user.getThemePreference()).isEqualTo(ThemePreference.DARK);
        assertThat(user.getAccentColor()).isEqualTo("#1A2B3C");

        user.updatePreferences(null, "#abcdef");
        assertThat(user.getThemePreference()).isEqualTo(ThemePreference.DARK);
        assertThat(user.getAccentColor()).isEqualTo("#abcdef");

        assertThatThrownBy(() -> user.updatePreferences(null, "not-a-color"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void markEmailVerifiedIsIdempotent() {
        User user = User.createMember(
                UUID.randomUUID(),
                "user@familion.test",
                "{bcrypt}hash",
                "User",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY,
                null);
        Instant first = Instant.parse("2026-05-01T10:00:00Z");
        Instant second = Instant.parse("2026-05-02T10:00:00Z");

        assertThat(user.isEmailVerified()).isFalse();
        user.markEmailVerified(first);
        user.markEmailVerified(second);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isEqualTo(first);
    }

    @Test
    void updatesChildVisibilityAndRejectsAdultVisibilityChange() {
        User child = User.createMember(
                UUID.randomUUID(),
                "child@familion.test",
                "{bcrypt}hash",
                "Child",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY,
                null);
        child.updateFamilyVisibility(FamilyVisibility.FAMILY_SUMMARY);
        assertThat(child.getFamilyVisibility()).isEqualTo(FamilyVisibility.FAMILY_SUMMARY);

        User adult = User.createMember(
                UUID.randomUUID(),
                "adult@familion.test",
                "{bcrypt}hash",
                "Adult",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL,
                null);
        assertThatThrownBy(() -> adult.updateFamilyVisibility(FamilyVisibility.FAMILY_FULL))
                .isInstanceOf(ConflictException.class);
    }
}
