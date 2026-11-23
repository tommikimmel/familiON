package com.familion.economics.infrastructure.persistence;

import com.familion.common.money.Money;
import com.familion.economics.domain.IndexCode;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC batch upsert helper for bulk API synchronization and IPC chain persistence.
 */
@Repository
public class EconomicIndexBatchRepository {

    private static final String UPSERT_API_SQL =
            """
            INSERT INTO economic_indices (id, index_code, effective_date, value, source, created_at)
            VALUES (gen_random_uuid(), ?, ?, ?, 'API', NOW())
            ON CONFLICT (index_code, effective_date)
            DO UPDATE SET value = EXCLUDED.value
            WHERE economic_indices.source <> 'MANUAL'
            """;

    private static final int BATCH_SIZE = 200;

    private final JdbcTemplate jdbcTemplate;

    public EconomicIndexBatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record UpsertEntry(IndexCode indexCode, LocalDate effectiveDate, BigDecimal value) {}

    @Transactional
    public void batchUpsertFromApi(List<UpsertEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(UPSERT_API_SQL, entries, BATCH_SIZE, (ps, entry) -> {
            ps.setString(1, entry.indexCode().name());
            ps.setDate(2, Date.valueOf(entry.effectiveDate()));
            ps.setBigDecimal(3, Money.rate(entry.value()));
        });
    }
}
