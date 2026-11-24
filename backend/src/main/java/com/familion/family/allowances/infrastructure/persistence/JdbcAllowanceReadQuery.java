package com.familion.family.allowances.infrastructure.persistence;

import com.familion.common.money.Money;
import com.familion.family.allowances.application.AllowanceReadQuery;
import com.familion.family.allowances.application.AllowanceReadRow;
import com.familion.family.allowances.domain.AllowanceAdjustmentRule;
import com.familion.family.allowances.domain.AllowanceFrequency;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcAllowanceReadQuery implements AllowanceReadQuery {

    private static final String BASE_SELECT_SQL = """
            SELECT
                al.id,
                al.family_id,
                al.child_user_id,
                cu.full_name AS child_full_name,
                cu.accent_color AS child_accent_color,
                al.assigned_by_user_id,
                ab.full_name AS assigned_by_full_name,
                al.source_account_id,
                sa.name AS source_account_name,
                al.target_account_id,
                ta.name AS target_account_name,
                al.amount,
                al.currency_code,
                al.frequency,
                al.day_of_period,
                al.adjustment_rule,
                al.is_active,
                al.created_at
            FROM allowances al
            JOIN users cu ON cu.id = al.child_user_id
            JOIN users ab ON ab.id = al.assigned_by_user_id
            LEFT JOIN accounts sa ON sa.id = al.source_account_id
            JOIN accounts ta ON ta.id = al.target_account_id
            WHERE al.family_id = :familyId
            """;

    private static final String FIND_LAST_DISBURSEMENT_DATES_SQL = """
            SELECT
                t.allowance_id,
                MAX(t.transaction_date) AS last_disbursement_date
            FROM transactions t
            WHERE t.allowance_id IN (:allowanceIds)
              AND t.type = 'ALLOWANCE_IN'
              AND t.status = 'POSTED'
              AND t.deleted_at IS NULL
            GROUP BY t.allowance_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAllowanceReadQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AllowanceReadRow> findVisibleForUser(
            UUID familyId, UUID userId, boolean adult, boolean includeInactive) {
        StringBuilder sql = new StringBuilder(BASE_SELECT_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource("familyId", familyId);

        if (!adult) {
            sql.append(" AND al.child_user_id = :userId");
            params.addValue("userId", userId);
        }

        if (!includeInactive) {
            sql.append(" AND al.is_active = TRUE");
        }

        sql.append(" ORDER BY LOWER(cu.full_name) ASC, al.created_at ASC, al.id ASC");

        return jdbcTemplate.query(sql.toString(), params, JdbcAllowanceReadQuery::mapReadRow);
    }

    @Override
    public Optional<AllowanceReadRow> findById(UUID familyId, UUID allowanceId) {
        String sql = BASE_SELECT_SQL + " AND al.id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("id", allowanceId);
        List<AllowanceReadRow> rows =
                jdbcTemplate.query(sql, params, JdbcAllowanceReadQuery::mapReadRow);
        return rows.stream().findFirst();
    }

    @Override
    public Map<UUID, LocalDate> findLastDisbursementDates(Collection<UUID> allowanceIds) {
        if (allowanceIds == null || allowanceIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("allowanceIds", allowanceIds);
        Map<UUID, LocalDate> result = new HashMap<>();
        jdbcTemplate.query(FIND_LAST_DISBURSEMENT_DATES_SQL, params, rs -> {
            UUID allowanceId = rs.getObject("allowance_id", UUID.class);
            LocalDate lastDate = rs.getObject("last_disbursement_date", LocalDate.class);
            if (allowanceId != null && lastDate != null) {
                result.put(allowanceId, lastDate);
            }
        });
        return result;
    }

    private static AllowanceReadRow mapReadRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime createdAtOffset = rs.getObject("created_at", OffsetDateTime.class);
        Instant createdAt = createdAtOffset != null ? createdAtOffset.toInstant() : Instant.EPOCH;

        return new AllowanceReadRow(
                rs.getObject("id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getObject("child_user_id", UUID.class),
                rs.getString("child_full_name"),
                rs.getString("child_accent_color"),
                rs.getObject("assigned_by_user_id", UUID.class),
                rs.getString("assigned_by_full_name"),
                rs.getObject("source_account_id", UUID.class),
                rs.getString("source_account_name"),
                rs.getObject("target_account_id", UUID.class),
                rs.getString("target_account_name"),
                Money.scale(rs.getBigDecimal("amount")),
                rs.getString("currency_code"),
                AllowanceFrequency.valueOf(rs.getString("frequency")),
                rs.getInt("day_of_period"),
                AllowanceAdjustmentRule.valueOf(rs.getString("adjustment_rule")),
                rs.getBoolean("is_active"),
                createdAt);
    }
}
