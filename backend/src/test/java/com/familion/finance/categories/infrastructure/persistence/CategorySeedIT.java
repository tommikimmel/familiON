package com.familion.finance.categories.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.test.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CategorySeedIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationSeedsAllThirtyOneSystemCategoriesAndLinksVehicleChildren() {
        List<Map<String, Object>> systemCategories = jdbcTemplate.queryForList(
                """
                SELECT id, family_id, parent_category_id, name, kind, icon, is_system, is_active, system_key
                FROM categories
                WHERE is_system = TRUE
                ORDER BY system_key
                """);

        assertThat(systemCategories).hasSize(31);
        assertThat(systemCategories).allSatisfy(row -> {
            assertThat(row.get("family_id")).isNull();
            assertThat(row.get("is_system")).isEqualTo(true);
            assertThat(row.get("is_active")).isEqualTo(true);
            assertThat((String) row.get("system_key")).isNotBlank();
            assertThat((String) row.get("icon")).isNotBlank();
        });

        long expenseCount = systemCategories.stream()
                .filter(row -> "EXPENSE".equals(row.get("kind")))
                .count();
        long incomeCount = systemCategories.stream()
                .filter(row -> "INCOME".equals(row.get("kind")))
                .count();
        assertThat(expenseCount).isEqualTo(24);
        assertThat(incomeCount).isEqualTo(7);

        UUID vehiclesId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE system_key = 'VEHICLES'", UUID.class);
        assertThat(vehiclesId).isNotNull();

        List<String> vehicleChildKeys = jdbcTemplate.queryForList(
                """
                SELECT system_key
                FROM categories
                WHERE parent_category_id = ?
                ORDER BY system_key
                """,
                String.class,
                vehiclesId);

        assertThat(vehicleChildKeys)
                .containsExactlyInAnyOrder(
                        "VEHICLES_FUEL",
                        "VEHICLES_MAINTENANCE",
                        "VEHICLES_INSURANCE",
                        "VEHICLES_TAXES",
                        "VEHICLES_TOLLS",
                        "VEHICLES_FINES");
    }
}
