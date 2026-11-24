package com.familion.finance.recurring.infrastructure.persistence;

import com.familion.common.money.Money;
import com.familion.finance.recurring.application.RecurringDueCandidateRow;
import com.familion.finance.recurring.application.RecurringPaymentRow;
import com.familion.finance.recurring.application.RecurringReadQuery;
import com.familion.finance.recurring.application.RecurringReadRow;
import com.familion.finance.recurring.domain.AdjustmentRule;
import com.familion.finance.transactions.application.api.Scope;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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
public class JdbcRecurringReadQuery implements RecurringReadQuery {

    private static final String BASE_SELECT_SQL = """
            SELECT
                re.id,
                re.family_id,
                re.created_by_user_id,
                re.name,
                re.category_id,
                c.name AS category_name,
                c.icon AS category_icon,
                c.color_hex AS category_color_hex,
                re.account_id,
                a.name AS account_name,
                re.scope,
                re.base_amount,
                re.currency_code,
                re.due_day,
                re.adjustment_rule,
                re.start_date,
                re.end_date,
                re.is_active
            FROM recurring_expenses re
            JOIN categories c ON c.id = re.category_id
            LEFT JOIN accounts a ON a.id = re.account_id
            WHERE re.family_id = :familyId
            """;

    private static final String FIND_PAYMENTS_IN_RANGE_SQL = """
            SELECT DISTINCT ON (t.recurring_expense_id)
                t.recurring_expense_id,
                t.id AS transaction_id,
                t.amount,
                t.transaction_date
            FROM transactions t
            WHERE t.recurring_expense_id IN (:recurringExpenseIds)
              AND t.deleted_at IS NULL
              AND t.status = 'POSTED'
              AND t.transaction_date >= :fromDate
              AND t.transaction_date <= :toDate
            ORDER BY t.recurring_expense_id, t.transaction_date DESC, t.created_at DESC, t.id DESC
            """;

    private static final String FIND_LATEST_PAID_BEFORE_SQL = """
            SELECT DISTINCT ON (t.recurring_expense_id)
                t.recurring_expense_id,
                t.amount
            FROM transactions t
            WHERE t.recurring_expense_id IN (:recurringExpenseIds)
              AND t.deleted_at IS NULL
              AND t.status = 'POSTED'
              AND t.transaction_date < :beforeDate
            ORDER BY t.recurring_expense_id, t.transaction_date DESC, t.created_at DESC, t.id DESC
            """;

    private static final String EXISTS_PAYMENT_IN_RANGE_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM transactions t
                WHERE t.family_id = :familyId
                  AND t.recurring_expense_id = :recurringExpenseId
                  AND t.deleted_at IS NULL
                  AND t.status = 'POSTED'
                  AND t.transaction_date >= :fromDate
                  AND t.transaction_date <= :toDate
            )
            """;

    private static final String FIND_ALL_ACTIVE_CANDIDATES_SQL = """
            SELECT
                re.id,
                re.family_id,
                re.name,
                re.base_amount,
                re.currency_code,
                re.due_day,
                re.adjustment_rule,
                re.start_date,
                re.end_date
            FROM recurring_expenses re
            WHERE re.is_active = TRUE
              AND re.start_date <= :lastMonthEnd
              AND (re.end_date IS NULL OR re.end_date >= :firstMonthStart)
            ORDER BY re.due_day ASC, LOWER(re.name) ASC, re.id ASC
            """;

    private static final String FIND_ALL_PAYMENTS_UP_TO_SQL = """
            SELECT
                t.recurring_expense_id,
                t.id AS transaction_id,
                t.amount,
                t.transaction_date
            FROM transactions t
            WHERE t.recurring_expense_id IN (:recurringExpenseIds)
              AND t.deleted_at IS NULL
              AND t.status = 'POSTED'
              AND t.transaction_date <= :upToDate
            ORDER BY t.recurring_expense_id, t.transaction_date DESC, t.created_at DESC, t.id DESC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRecurringReadQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RecurringReadRow> findVisibleForUser(
            UUID familyId, UUID userId, boolean adult, boolean includeInactive) {
        StringBuilder sql = new StringBuilder(BASE_SELECT_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("userId", userId);

        if (adult) {
            sql.append(" AND (re.scope = 'FAMILY' OR (re.scope = 'PERSONAL' AND re.created_by_user_id = :userId))");
        } else {
            sql.append(" AND re.scope = 'PERSONAL' AND re.created_by_user_id = :userId");
        }

        if (!includeInactive) {
            sql.append(" AND re.is_active = TRUE");
        }

        sql.append(" ORDER BY re.due_day ASC, LOWER(re.name) ASC, re.id ASC");

        return jdbcTemplate.query(sql.toString(), params, JdbcRecurringReadQuery::mapReadRow);
    }

    @Override
    public Optional<RecurringReadRow> findById(UUID familyId, UUID recurringExpenseId) {
        String sql = BASE_SELECT_SQL + " AND re.id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("id", recurringExpenseId);
        List<RecurringReadRow> rows =
                jdbcTemplate.query(sql, params, JdbcRecurringReadQuery::mapReadRow);
        return rows.stream().findFirst();
    }

    @Override
    public Map<UUID, RecurringPaymentRow> findPaymentsInRange(
            Collection<UUID> recurringExpenseIds, LocalDate from, LocalDate to) {
        if (recurringExpenseIds == null || recurringExpenseIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recurringExpenseIds", recurringExpenseIds)
                .addValue("fromDate", from)
                .addValue("toDate", to);

        Map<UUID, RecurringPaymentRow> result = new HashMap<>();
        jdbcTemplate.query(FIND_PAYMENTS_IN_RANGE_SQL, params, rs -> {
            RecurringPaymentRow row = mapPaymentRow(rs);
            result.put(row.recurringExpenseId(), row);
        });
        return result;
    }

    @Override
    public Map<UUID, BigDecimal> findLatestPaidAmountBefore(
            Collection<UUID> recurringExpenseIds, LocalDate beforeDate) {
        if (recurringExpenseIds == null || recurringExpenseIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recurringExpenseIds", recurringExpenseIds)
                .addValue("beforeDate", beforeDate);

        Map<UUID, BigDecimal> result = new HashMap<>();
        jdbcTemplate.query(FIND_LATEST_PAID_BEFORE_SQL, params, rs -> {
            UUID recurringId = rs.getObject("recurring_expense_id", UUID.class);
            BigDecimal amount = Money.scale(rs.getBigDecimal("amount"));
            result.put(recurringId, amount);
        });
        return result;
    }

    @Override
    public boolean existsPaymentInRange(
            UUID familyId, UUID recurringExpenseId, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("recurringExpenseId", recurringExpenseId)
                .addValue("fromDate", from)
                .addValue("toDate", to);
        Boolean exists =
                jdbcTemplate.queryForObject(EXISTS_PAYMENT_IN_RANGE_SQL, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public List<RecurringDueCandidateRow> findAllActiveCandidates(
            LocalDate firstMonthStart, LocalDate lastMonthEnd) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("firstMonthStart", firstMonthStart)
                .addValue("lastMonthEnd", lastMonthEnd);
        return jdbcTemplate.query(
                FIND_ALL_ACTIVE_CANDIDATES_SQL, params, JdbcRecurringReadQuery::mapCandidateRow);
    }

    @Override
    public List<RecurringPaymentRow> findAllPaymentsUpTo(
            Collection<UUID> recurringExpenseIds, LocalDate upToDate) {
        if (recurringExpenseIds == null || recurringExpenseIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recurringExpenseIds", recurringExpenseIds)
                .addValue("upToDate", upToDate);
        return jdbcTemplate.query(
                FIND_ALL_PAYMENTS_UP_TO_SQL, params, (rs, rowNum) -> mapPaymentRow(rs));
    }

    private static RecurringReadRow mapReadRow(ResultSet rs, int rowNum) throws SQLException {
        return new RecurringReadRow(
                rs.getObject("id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getObject("created_by_user_id", UUID.class),
                rs.getString("name"),
                rs.getObject("category_id", UUID.class),
                rs.getString("category_name"),
                rs.getString("category_icon"),
                rs.getString("category_color_hex"),
                rs.getObject("account_id", UUID.class),
                rs.getString("account_name"),
                Scope.valueOf(rs.getString("scope")),
                Money.scale(rs.getBigDecimal("base_amount")),
                rs.getString("currency_code"),
                rs.getInt("due_day"),
                AdjustmentRule.valueOf(rs.getString("adjustment_rule")),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getBoolean("is_active"));
    }

    private static RecurringPaymentRow mapPaymentRow(ResultSet rs) throws SQLException {
        return new RecurringPaymentRow(
                rs.getObject("recurring_expense_id", UUID.class),
                rs.getObject("transaction_id", UUID.class),
                Money.scale(rs.getBigDecimal("amount")),
                rs.getObject("transaction_date", LocalDate.class));
    }

    private static RecurringDueCandidateRow mapCandidateRow(ResultSet rs, int rowNum)
            throws SQLException {
        return new RecurringDueCandidateRow(
                rs.getObject("id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getString("name"),
                Money.scale(rs.getBigDecimal("base_amount")),
                rs.getString("currency_code"),
                rs.getInt("due_day"),
                AdjustmentRule.valueOf(rs.getString("adjustment_rule")),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class));
    }
}
