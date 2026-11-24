package com.familion.analytics.infrastructure.persistence;

import com.familion.analytics.application.AnalyticsQuery;
import com.familion.analytics.application.CategoryAggregateRow;
import com.familion.analytics.application.PeriodAggregateRow;
import com.familion.analytics.application.PersonAggregateRow;
import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.domain.AnalyticsLensCalculator;
import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcAnalyticsQuery implements AnalyticsQuery {

    private static final List<String> PERSONAL_INCOME_TYPES = List.of("INCOME", "ALLOWANCE_IN");
    private static final List<String> FAMILY_INCOME_TYPES = List.of("INCOME");
    private static final List<String> PERSONAL_RELEVANT_TYPES =
            List.of("INCOME", "ALLOWANCE_IN", "EXPENSE");
    private static final List<String> FAMILY_RELEVANT_TYPES = List.of("INCOME", "EXPENSE");

    private static final String NOMINAL_EXPR = "COALESCE(es.base_currency_amount, t.amount)";
    private static final String REFERENCE_EXPR = "COALESCE(es.amount_in_reference, t.amount)";
    private static final String CONSTANT_BASE_WITH_IPC_EXPR = """
            CASE
                WHEN es.base_currency_amount IS NOT NULL AND es.ipc_index_value IS NOT NULL AND es.ipc_index_value > 0
                    THEN es.base_currency_amount * :latestIpc / es.ipc_index_value
                ELSE COALESCE(es.base_currency_amount, t.amount)
            END
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAnalyticsQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PeriodAggregateRow> aggregateByPeriod(
            UUID familyId,
            UUID viewerUserId,
            boolean includeAllowanceIn,
            AnalyticsGranularity granularity,
            AnalyticsLens lens,
            BigDecimal latestIpc,
            LocalDate from,
            LocalDate to) {
        boolean hasLatestIpc = AnalyticsLensCalculator.isPositive(latestIpc);
        String lensExpr = lensAmountExpression(lens, hasLatestIpc);
        String constantBaseExpr = hasLatestIpc ? CONSTANT_BASE_WITH_IPC_EXPR : NOMINAL_EXPR;
        String approxExpr = approximatedExpression(lens, hasLatestIpc);
        String dateTruncUnit = granularity.sqlDateTruncUnit();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DATE_TRUNC('")
                .append(dateTruncUnit)
                .append("', t.transaction_date)::date AS period_start, ")
                .append("COALESCE(SUM(CASE WHEN t.type IN (:incomeTypes) THEN ")
                .append(lensExpr)
                .append(" ELSE 0 END), 0) AS lens_income, ")
                .append("COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN ")
                .append(lensExpr)
                .append(" ELSE 0 END), 0) AS lens_expense, ")
                .append("COALESCE(SUM(CASE WHEN t.type IN (:incomeTypes) THEN ")
                .append(NOMINAL_EXPR)
                .append(" ELSE 0 END), 0) AS nominal_income, ")
                .append("COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN ")
                .append(NOMINAL_EXPR)
                .append(" ELSE 0 END), 0) AS nominal_expense, ")
                .append("COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN ")
                .append(constantBaseExpr)
                .append(" ELSE 0 END), 0) AS constant_base_expense, ")
                .append("COALESCE(")
                .append(approxExpr)
                .append(", FALSE) AS lens_approximated ")
                .append("FROM transactions t ")
                .append("LEFT JOIN economic_snapshots es ON es.transaction_id = t.id ")
                .append("WHERE t.family_id = :familyId ")
                .append("AND t.deleted_at IS NULL ")
                .append("AND t.status = 'POSTED' ")
                .append("AND t.type IN (:relevantTypes) ")
                .append("AND t.transaction_date >= :fromDate ")
                .append("AND t.transaction_date <= :toDate ");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("incomeTypes", includeAllowanceIn ? PERSONAL_INCOME_TYPES : FAMILY_INCOME_TYPES)
                .addValue(
                        "relevantTypes",
                        includeAllowanceIn ? PERSONAL_RELEVANT_TYPES : FAMILY_RELEVANT_TYPES)
                .addValue("fromDate", from)
                .addValue("toDate", to);

        if (hasLatestIpc) {
            params.addValue("latestIpc", latestIpc, Types.NUMERIC);
        }
        if (viewerUserId != null) {
            sql.append("AND t.attributed_to_user_id = :viewerUserId ");
            params.addValue("viewerUserId", viewerUserId);
        }

        sql.append("GROUP BY period_start ORDER BY period_start ASC");

        return jdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> new PeriodAggregateRow(
                        rs.getObject("period_start", LocalDate.class),
                        Money.scale(rs.getBigDecimal("lens_income")),
                        Money.scale(rs.getBigDecimal("lens_expense")),
                        Money.scale(rs.getBigDecimal("nominal_income")),
                        Money.scale(rs.getBigDecimal("nominal_expense")),
                        Money.scale(rs.getBigDecimal("constant_base_expense")),
                        rs.getBoolean("lens_approximated")));
    }

    @Override
    public BigDecimal sumProjectedInstallments(
            UUID familyId, UUID viewerUserId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(COALESCE(es.base_currency_amount, t.amount)), 0) AS total
                FROM transactions t
                LEFT JOIN economic_snapshots es ON es.transaction_id = t.id
                WHERE t.family_id = :familyId
                  AND t.deleted_at IS NULL
                  AND t.status = 'PROJECTED'
                  AND t.type = 'EXPENSE'
                  AND t.installment_plan_id IS NOT NULL
                  AND t.transaction_date >= :fromDate
                  AND t.transaction_date <= :toDate
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("fromDate", from)
                .addValue("toDate", to);

        if (viewerUserId != null) {
            sql.append(" AND t.attributed_to_user_id = :viewerUserId");
            params.addValue("viewerUserId", viewerUserId);
        }

        BigDecimal total = jdbcTemplate.queryForObject(sql.toString(), params, BigDecimal.class);
        return total == null ? AnalyticsLensCalculator.ZERO : Money.scale(total);
    }

    @Override
    public BigDecimal sumNominalIncome(
            UUID familyId,
            UUID viewerUserId,
            boolean includeAllowanceIn,
            LocalDate from,
            LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(COALESCE(es.base_currency_amount, t.amount)), 0) AS total
                FROM transactions t
                LEFT JOIN economic_snapshots es ON es.transaction_id = t.id
                WHERE t.family_id = :familyId
                  AND t.deleted_at IS NULL
                  AND t.status = 'POSTED'
                  AND t.type IN (:incomeTypes)
                  AND t.transaction_date >= :fromDate
                  AND t.transaction_date <= :toDate
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue(
                        "incomeTypes",
                        includeAllowanceIn ? PERSONAL_INCOME_TYPES : FAMILY_INCOME_TYPES)
                .addValue("fromDate", from)
                .addValue("toDate", to);

        if (viewerUserId != null) {
            sql.append(" AND t.attributed_to_user_id = :viewerUserId");
            params.addValue("viewerUserId", viewerUserId);
        }

        BigDecimal total = jdbcTemplate.queryForObject(sql.toString(), params, BigDecimal.class);
        return total == null ? AnalyticsLensCalculator.ZERO : Money.scale(total);
    }

    @Override
    public List<CategoryAggregateRow> aggregateByCategory(
            UUID familyId,
            UUID viewerUserId,
            AnalyticsLens lens,
            BigDecimal latestIpc,
            LocalDate from,
            LocalDate to) {
        boolean hasLatestIpc = AnalyticsLensCalculator.isPositive(latestIpc);
        String lensExpr = lensAmountExpression(lens, hasLatestIpc);
        String constantBaseExpr = hasLatestIpc ? CONSTANT_BASE_WITH_IPC_EXPR : NOMINAL_EXPR;

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(p.id, c.id) AS root_category_id, ")
                .append("COALESCE(p.name, c.name) AS root_name, ")
                .append("COALESCE(p.color_hex, c.color_hex) AS root_color_hex, ")
                .append("COALESCE(p.icon, c.icon) AS root_icon, ")
                .append("COALESCE(SUM(")
                .append(lensExpr)
                .append("), 0) AS lens_amount, ")
                .append("COALESCE(SUM(")
                .append(NOMINAL_EXPR)
                .append("), 0) AS nominal_amount, ")
                .append("COALESCE(SUM(")
                .append(constantBaseExpr)
                .append("), 0) AS constant_base_amount ")
                .append("FROM transactions t ")
                .append("LEFT JOIN economic_snapshots es ON es.transaction_id = t.id ")
                .append("LEFT JOIN categories c ON c.id = t.category_id ")
                .append("LEFT JOIN categories p ON p.id = c.parent_category_id ")
                .append("WHERE t.family_id = :familyId ")
                .append("AND t.deleted_at IS NULL ")
                .append("AND t.status = 'POSTED' ")
                .append("AND t.type = 'EXPENSE' ")
                .append("AND t.transaction_date >= :fromDate ")
                .append("AND t.transaction_date <= :toDate ");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("fromDate", from)
                .addValue("toDate", to);

        if (hasLatestIpc) {
            params.addValue("latestIpc", latestIpc, Types.NUMERIC);
        }
        if (viewerUserId != null) {
            sql.append("AND t.attributed_to_user_id = :viewerUserId ");
            params.addValue("viewerUserId", viewerUserId);
        }

        sql.append("GROUP BY root_category_id, root_name, root_color_hex, root_icon ")
                .append("ORDER BY lens_amount DESC, COALESCE(p.name, c.name, 'Sin categoría') ASC");

        return jdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> new CategoryAggregateRow(
                        rs.getObject("root_category_id", UUID.class),
                        rs.getString("root_name"),
                        rs.getString("root_color_hex"),
                        rs.getString("root_icon"),
                        Money.scale(rs.getBigDecimal("lens_amount")),
                        Money.scale(rs.getBigDecimal("nominal_amount")),
                        Money.scale(rs.getBigDecimal("constant_base_amount"))));
    }

    @Override
    public Map<UUID, BigDecimal> aggregateCategoryConstantBase(
            UUID familyId, UUID viewerUserId, BigDecimal latestIpc, LocalDate from, LocalDate to) {
        boolean hasLatestIpc = AnalyticsLensCalculator.isPositive(latestIpc);
        String constantBaseExpr = hasLatestIpc ? CONSTANT_BASE_WITH_IPC_EXPR : NOMINAL_EXPR;

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(p.id, c.id) AS root_category_id, ")
                .append("COALESCE(SUM(")
                .append(constantBaseExpr)
                .append("), 0) AS constant_base_amount ")
                .append("FROM transactions t ")
                .append("LEFT JOIN economic_snapshots es ON es.transaction_id = t.id ")
                .append("LEFT JOIN categories c ON c.id = t.category_id ")
                .append("LEFT JOIN categories p ON p.id = c.parent_category_id ")
                .append("WHERE t.family_id = :familyId ")
                .append("AND t.deleted_at IS NULL ")
                .append("AND t.status = 'POSTED' ")
                .append("AND t.type = 'EXPENSE' ")
                .append("AND t.transaction_date >= :fromDate ")
                .append("AND t.transaction_date <= :toDate ");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("fromDate", from)
                .addValue("toDate", to);

        if (hasLatestIpc) {
            params.addValue("latestIpc", latestIpc, Types.NUMERIC);
        }
        if (viewerUserId != null) {
            sql.append("AND t.attributed_to_user_id = :viewerUserId ");
            params.addValue("viewerUserId", viewerUserId);
        }

        sql.append("GROUP BY root_category_id");

        Map<UUID, BigDecimal> totals = new HashMap<>();
        jdbcTemplate.query(sql.toString(), params, rs -> {
            UUID rootId = rs.getObject("root_category_id", UUID.class);
            BigDecimal amount = Money.scale(rs.getBigDecimal("constant_base_amount"));
            totals.put(rootId, amount);
        });
        return totals;
    }

    @Override
    public List<PersonAggregateRow> aggregateByPerson(
            UUID familyId,
            AnalyticsLens lens,
            BigDecimal latestIpc,
            LocalDate from,
            LocalDate to) {
        boolean hasLatestIpc = AnalyticsLensCalculator.isPositive(latestIpc);
        String lensExpr = lensAmountExpression(lens, hasLatestIpc);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT u.id AS user_id, ")
                .append("u.full_name, ")
                .append("u.accent_color, ")
                .append("COALESCE(SUM(")
                .append(lensExpr)
                .append("), 0) AS lens_amount, ")
                .append("COALESCE(SUM(")
                .append(NOMINAL_EXPR)
                .append("), 0) AS nominal_amount ")
                .append("FROM transactions t ")
                .append("JOIN users u ON u.id = t.attributed_to_user_id ")
                .append("LEFT JOIN economic_snapshots es ON es.transaction_id = t.id ")
                .append("WHERE t.family_id = :familyId ")
                .append("AND t.deleted_at IS NULL ")
                .append("AND t.status = 'POSTED' ")
                .append("AND t.type = 'EXPENSE' ")
                .append("AND t.transaction_date >= :fromDate ")
                .append("AND t.transaction_date <= :toDate ")
                .append("GROUP BY u.id, u.full_name, u.accent_color ")
                .append("ORDER BY lens_amount DESC, u.full_name ASC");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("fromDate", from)
                .addValue("toDate", to);

        if (hasLatestIpc) {
            params.addValue("latestIpc", latestIpc, Types.NUMERIC);
        }

        return jdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> new PersonAggregateRow(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("accent_color"),
                        Money.scale(rs.getBigDecimal("lens_amount")),
                        Money.scale(rs.getBigDecimal("nominal_amount"))));
    }

    static String lensAmountExpression(AnalyticsLens lens, boolean hasLatestIpc) {
        return switch (lens) {
            case NOMINAL, INCOME_PCT -> NOMINAL_EXPR;
            case REFERENCE_CURRENCY -> REFERENCE_EXPR;
            case CONSTANT_BASE -> hasLatestIpc ? CONSTANT_BASE_WITH_IPC_EXPR : NOMINAL_EXPR;
        };
    }

    static String approximatedExpression(AnalyticsLens lens, boolean hasLatestIpc) {
        return switch (lens) {
            case NOMINAL, INCOME_PCT -> "BOOL_OR(es.base_currency_amount IS NULL)";
            case REFERENCE_CURRENCY -> "BOOL_OR(es.amount_in_reference IS NULL)";
            case CONSTANT_BASE -> hasLatestIpc
                    ? "BOOL_OR(es.base_currency_amount IS NULL OR es.ipc_index_value IS NULL OR es.ipc_index_value <= 0)"
                    : "COUNT(*) > 0";
        };
    }
}
