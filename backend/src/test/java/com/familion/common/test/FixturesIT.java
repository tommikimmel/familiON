package com.familion.common.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FixturesIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsFamilyWithArsAndUsdCurrenciesUserAndAccount() {
        UUID familyId = fixtures.createFamily("Familia Test " + UUID.randomUUID());
        UUID userId = fixtures.createUser(
                familyId,
                "adult-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID accountId = fixtures.createAccount(familyId, userId, "BANK", "ARS");

        List<String> currencies = jdbcTemplate.queryForList(
                "SELECT currency_code FROM family_currencies WHERE family_id = ? AND is_active = TRUE",
                String.class,
                familyId);
        assertThat(currencies).containsExactlyInAnyOrder("ARS", "USD");

        Boolean twoFactorEnabled = jdbcTemplate.queryForObject(
                "SELECT two_factor_enabled FROM users WHERE id = ?", Boolean.class, userId);
        assertThat(twoFactorEnabled).isFalse();

        Integer accountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE id = ? AND family_id = ? AND owner_user_id = ?",
                Integer.class,
                accountId,
                familyId,
                userId);
        assertThat(accountCount).isEqualTo(1);

        String bearer = tokens.bearer(userId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        assertThat(bearer).startsWith("Bearer ");
    }
}
