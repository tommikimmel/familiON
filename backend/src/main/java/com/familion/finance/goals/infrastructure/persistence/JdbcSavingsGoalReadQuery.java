package com.familion.finance.goals.infrastructure.persistence;

import com.familion.common.money.Money;
import com.familion.finance.goals.application.ContributionReadRow;
import com.familion.finance.goals.application.GoalListingScope;
import com.familion.finance.goals.application.SavingsGoalReadQuery;
import com.familion.finance.goals.application.SavingsGoalReadRow;
import com.familion.finance.goals.domain.GoalScope;
import com.familion.finance.goals.domain.GoalStatus;
import com.familion.finance.goals.domain.TargetUnit;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcSavingsGoalReadQuery implements SavingsGoalReadQuery {

    private static final String GOALS_BASE_SQL = """
            SELECT
                g.id,
                g.name,
                g.scope,
                g.owner_user_id,
                u.full_name AS owner_full_name,
                g.target_amount,
                g.target_unit,
                g.target_date,
                g.status,
                g.created_at,
                COALESCE(agg.accumulated, 0) AS accumulated_in_unit,
                COALESCE(agg.contributions_count, 0) AS contributions_count,
                agg.last_contribution_date
            FROM savings_goals g
            LEFT JOIN users u ON u.id = g.owner_user_id
            LEFT JOIN (
                SELECT
                    c.savings_goal_id,
                    SUM(c.amount_in_goal_unit) AS accumulated,
                    COUNT(*) AS contributions_count,
                    MAX(c.contribution_date) AS last_contribution_date
                FROM goal_contributions c
                GROUP BY c.savings_goal_id
            ) agg ON agg.savings_goal_id = g.id
            WHERE g.family_id = :familyId
            """;

    private static final String CONTRIBUTIONS_BY_GOAL_SQL = """
            SELECT
                c.id,
                c.user_id,
                u.full_name AS user_full_name,
                c.amount,
                c.currency_code,
                c.amount_in_goal_unit,
                c.contribution_date,
                c.notes,
                c.transaction_id
            FROM goal_contributions c
            JOIN users u ON u.id = c.user_id
            WHERE c.savings_goal_id = :goalId
            ORDER BY c.contribution_date DESC, c.created_at DESC, c.id ASC
            """;

    private static final String TRANSACTION_EXISTS_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM transactions t
                WHERE t.id = :transactionId
                  AND t.family_id = :familyId
                  AND t.deleted_at IS NULL
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSavingsGoalReadQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SavingsGoalReadRow> findGoals(
            UUID familyId, UUID userId, GoalListingScope listingScope, GoalStatus statusFilter) {
        StringBuilder sql = new StringBuilder(GOALS_BASE_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource("familyId", familyId);

        switch (listingScope) {
            case OWN_PERSONAL -> {
                sql.append(" AND g.scope = 'PERSONAL' AND g.owner_user_id = :userId");
                params.addValue("userId", userId);
            }
            case FAMILY_SCOPE_ONLY -> sql.append(" AND g.scope = 'FAMILY'");
            case ALL_FAMILY -> {
                // No additional scope/owner filter: all goals in the family
            }
        }

        if (statusFilter != null) {
            sql.append(" AND g.status = :status");
            params.addValue("status", statusFilter.name());
        }

        sql.append(" ORDER BY g.created_at DESC, g.id ASC");

        return jdbcTemplate.query(sql.toString(), params, JdbcSavingsGoalReadQuery::mapGoalRow);
    }

    @Override
    public Optional<SavingsGoalReadRow> findGoalById(UUID familyId, UUID goalId) {
        String sql = GOALS_BASE_SQL + " AND g.id = :goalId";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("goalId", goalId);
        List<SavingsGoalReadRow> rows =
                jdbcTemplate.query(sql, params, JdbcSavingsGoalReadQuery::mapGoalRow);
        return rows.stream().findFirst();
    }

    @Override
    public List<ContributionReadRow> findContributionsByGoalId(UUID goalId) {
        return jdbcTemplate.query(
                CONTRIBUTIONS_BY_GOAL_SQL,
                new MapSqlParameterSource("goalId", goalId),
                JdbcSavingsGoalReadQuery::mapContributionRow);
    }

    @Override
    public boolean transactionExistsInFamily(UUID familyId, UUID transactionId) {
        Boolean exists = jdbcTemplate.queryForObject(
                TRANSACTION_EXISTS_SQL,
                new MapSqlParameterSource()
                        .addValue("familyId", familyId)
                        .addValue("transactionId", transactionId),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private static SavingsGoalReadRow mapGoalRow(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal accumulated = rs.getBigDecimal("accumulated_in_unit");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Instant createdAt = createdTs != null ? createdTs.toInstant() : Instant.EPOCH;
        return new SavingsGoalReadRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                GoalScope.valueOf(rs.getString("scope")),
                rs.getObject("owner_user_id", UUID.class),
                rs.getString("owner_full_name"),
                Money.scale(rs.getBigDecimal("target_amount")),
                TargetUnit.valueOf(rs.getString("target_unit")),
                getNullableLocalDate(rs, "target_date"),
                GoalStatus.valueOf(rs.getString("status")),
                createdAt,
                Money.scale(accumulated != null ? accumulated : BigDecimal.ZERO),
                rs.getInt("contributions_count"),
                getNullableLocalDate(rs, "last_contribution_date"));
    }

    private static ContributionReadRow mapContributionRow(ResultSet rs, int rowNum)
            throws SQLException {
        return new ContributionReadRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("user_full_name"),
                Money.scale(rs.getBigDecimal("amount")),
                rs.getString("currency_code"),
                Money.scale(rs.getBigDecimal("amount_in_goal_unit")),
                rs.getDate("contribution_date").toLocalDate(),
                rs.getString("notes"),
                rs.getObject("transaction_id", UUID.class));
    }

    private static LocalDate getNullableLocalDate(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }
}
