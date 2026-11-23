package com.familion.family.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.ConflictException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.MemberRef;
import com.familion.family.application.api.RegisteredUser;
import com.familion.family.application.api.UserAccount;
import com.familion.family.application.api.UserAccountApi;
import com.familion.family.application.api.UserProfileApi;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class FamilyAndUserApisIT extends IntegrationTestBase {

    @Autowired
    private FamilyQueryApi familyQueryApi;

    @Autowired
    private UserAccountApi userAccountApi;

    @Autowired
    private UserProfileApi userProfileApi;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registerFamilyWithAdultCreatesDefaultSettingsCurrenciesAndAdultUser() {
        String email = "  Founder-" + UUID.randomUUID() + "@Familion.TEST ";
        RegisteredUser registered = userProfileApi.registerFamilyWithAdult(
                email, "Password123", "Maria Perez", "Familia Perez");

        assertThat(registered.email()).isEqualTo(email.trim().toLowerCase());

        FamilySettings settings = familyQueryApi.getSettings(registered.familyId());
        assertThat(settings.name()).isEqualTo("Familia Perez");
        assertThat(settings.baseCurrency()).isEqualTo("ARS");
        assertThat(settings.referenceCurrency()).isEqualTo("USD");
        assertThat(settings.referenceRateSource()).isEqualTo("USD_MEP");
        assertThat(settings.timezone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
        assertThat(settings.enabledCurrencies()).containsExactly("ARS", "USD");

        String arsRateSource = jdbcTemplate.queryForObject(
                "SELECT preferred_rate_source FROM family_currencies WHERE family_id = ? AND currency_code = 'ARS'",
                String.class,
                registered.familyId());
        String usdRateSource = jdbcTemplate.queryForObject(
                "SELECT preferred_rate_source FROM family_currencies WHERE family_id = ? AND currency_code = 'USD'",
                String.class,
                registered.familyId());
        assertThat(arsRateSource).isNull();
        assertThat(usdRateSource).isEqualTo("USD_MEP");

        UserAccount account = userAccountApi.require(registered.userId());
        assertThat(account.role()).isEqualTo(Role.ADULTO);
        assertThat(account.visibility()).isEqualTo(FamilyVisibility.FAMILY_FULL);
        assertThat(account.emailVerified()).isFalse();
        assertThat(account.twoFactorEnabled()).isTrue();
        assertThat(account.active()).isTrue();
        assertThat(account.passwordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("Password123", account.passwordHash())).isTrue();
    }

    @Test
    void requireMemberThrowsNotFoundForUserFromAnotherFamilyOrInactiveUser() {
        UUID familyA = fixtures.createFamily("Family A " + UUID.randomUUID());
        UUID familyB = fixtures.createFamily("Family B " + UUID.randomUUID());
        UUID userA = fixtures.createUser(
                familyA,
                "user-a-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID userB = fixtures.createUser(
                familyB,
                "user-b-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);

        MemberRef member = familyQueryApi.requireMember(familyA, userA);
        assertThat(member.userId()).isEqualTo(userA);
        assertThat(member.familyId()).isEqualTo(familyA);

        assertThatThrownBy(() -> familyQueryApi.requireMember(familyA, userB))
                .isInstanceOf(NotFoundException.class);

        jdbcTemplate.update("UPDATE users SET is_active = FALSE WHERE id = ?", userA);
        assertThatThrownBy(() -> familyQueryApi.requireMember(familyA, userA))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void userAccountApiSupportsMemberCreationLookupVerificationPasswordAndTwoFactor() {
        UUID familyId = fixtures.createFamily("Family Ops " + UUID.randomUUID());
        String childEmail = "Child-" + UUID.randomUUID() + "@Familion.TEST";

        UUID childId = userAccountApi.createMember(
                familyId,
                childEmail,
                "Lucas Perez",
                "InitialPass1",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY,
                false);

        assertThat(userAccountApi.findByEmail("  " + childEmail.toUpperCase() + "  "))
                .isPresent()
                .get()
                .satisfies(acc -> {
                    assertThat(acc.id()).isEqualTo(childId);
                    assertThat(acc.email()).isEqualTo(childEmail.toLowerCase());
                    assertThat(acc.role()).isEqualTo(Role.HIJO);
                    assertThat(acc.visibility()).isEqualTo(FamilyVisibility.OWN_ONLY);
                    assertThat(acc.emailVerified()).isFalse();
                    assertThat(acc.twoFactorEnabled()).isTrue();
                });

        assertThatThrownBy(() -> userAccountApi.createMember(
                        familyId,
                        childEmail.toUpperCase(),
                        "Duplicate",
                        "InitialPass1",
                        Role.HIJO,
                        FamilyVisibility.OWN_ONLY,
                        true))
                .isInstanceOf(ConflictException.class);

        userAccountApi.markEmailVerified(childId);
        userAccountApi.changePassword(childId, "NewSecurePass9");
        userAccountApi.setTwoFactorEnabled(childId, false);

        UserAccount updated = userAccountApi.require(childId);
        assertThat(updated.emailVerified()).isTrue();
        assertThat(updated.twoFactorEnabled()).isFalse();
        assertThat(passwordEncoder.matches("NewSecurePass9", updated.passwordHash())).isTrue();

        List<MemberRef> members = familyQueryApi.listMembers(familyId);
        assertThat(members).extracting(MemberRef::userId).containsExactly(childId);
    }
}
