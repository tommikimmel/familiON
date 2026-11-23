package com.familion.common.test;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TestFixtures {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$testdummyhash000000000000000000000000000000000000000000";

    private final JdbcTemplate jdbcTemplate;

    public TestFixtures(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID createFamily(String name) {
        UUID familyId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO families (id, name, base_currency, reference_currency, reference_rate_source, timezone)
                VALUES (?, ?, 'ARS', 'USD', 'USD_MEP', 'America/Argentina/Buenos_Aires')
                """,
                familyId,
                name);
        jdbcTemplate.update(
                """
                INSERT INTO family_currencies (id, family_id, currency_code, preferred_rate_source, is_active)
                VALUES (?, ?, 'ARS', NULL, TRUE),
                       (?, ?, 'USD', 'USD_MEP', TRUE)
                """,
                UUID.randomUUID(),
                familyId,
                UUID.randomUUID(),
                familyId);
        return familyId;
    }

    public UUID createUser(UUID familyId, String email, Role role, FamilyVisibility vis) {
        UUID userId = UUID.randomUUID();
        String fullName = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id, family_id, email, password_hash, full_name, role, family_visibility,
                    email_verified_at, two_factor_enabled, is_active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), FALSE, TRUE)
                """,
                userId,
                familyId,
                email,
                DUMMY_PASSWORD_HASH,
                fullName,
                role.name(),
                vis.name());
        return userId;
    }

    public UUID createAccount(UUID familyId, UUID ownerUserId, String type, String currency) {
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    id, family_id, owner_user_id, name, account_type, currency_code,
                    initial_balance, is_active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                """,
                accountId,
                familyId,
                ownerUserId,
                "Test " + type + " " + currency,
                type,
                currency,
                BigDecimal.ZERO);
        return accountId;
    }
}
