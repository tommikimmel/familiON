package com.familion;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.test.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayMigrationIT extends IntegrationTestBase {

    private static final List<String> EXPECTED_TABLES = List.of(
            "families",
            "family_currencies",
            "users",
            "family_invitations",
            "refresh_tokens",
            "email_security_codes",
            "trusted_devices",
            "economic_indices",
            "accounts",
            "categories",
            "allowances",
            "installment_plans",
            "recurring_expenses",
            "currency_exchanges",
            "receipt_attachments",
            "transactions",
            "economic_snapshots",
            "savings_goals",
            "goal_contributions",
            "vehicles",
            "fuel_logs",
            "maintenance_logs",
            "notifications",
            "push_subscriptions",
            "app_releases");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void initialMigrationCreatesAllTwentyFiveExpectedTables() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """,
                String.class);

        assertThat(tables)
                .hasSize(25)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }
}
