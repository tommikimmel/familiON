package com.familion.analytics.infrastructure.persistence;

import com.familion.analytics.application.AnalyticsQuery;
import com.familion.analytics.application.CalendarRows;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                .append("ORDER BY lens_amount DESC, root_name ASC NULLS LAST");

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

    @Override
    public List<CalendarRows.DailyTotalRow> findCalendarDailyTotals(
            UUID familyId,
            UUID viewerUserId,
            boolean includeAllowanceIn,
            LocalDate from,
            LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    t.transaction_date,
                    COALESCE(SUM(CASE WHEN t.type IN (:incomeTypes)
                        THEN COALESCE(es.base_currency_amount, t.amount) ELSE 0 END), 0) AS income,
                    COALESCE(SUM(CASE WHEN t.type = 'EXPENSE'
                        THEN COALESCE(es.base_currency_amount, t.amount) ELSE 0 END), 0) AS expense
                FROM transactions t
                LEFT JOIN economic_snapshots es ON es.transaction_id = t.id
                WHERE t.family_id = :familyId
                  AND t.deleted_at IS NULL
                  AND t.status = 'POSTED'
                  AND t.type IN (:relevantTypes)
                  AND t.transaction_date >= :fromDate
                  AND t.transaction_date <= :toDate
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue(
                        "incomeTypes",
                        includeAllowanceIn ? PERSONAL_INCOME_TYPES : FAMILY_INCOME_TYPES)
                .addValue(
                        "relevantTypes",
                        includeAllowanceIn ? PERSONAL_RELEVANT_TYPES : FAMILY_RELEVANT_TYPES)
                .addValue("fromDate", from)
                .addValue("toDate", to);

        if (viewerUserId != null) {
            sql.append(" AND t.attributed_to_user_id = :viewerUserId");
            params.addValue("viewerUserId", viewerUserId);
        }

        sql.append(" GROUP BY t.transaction_date ORDER BY t.transaction_date ASC");

        return jdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> new CalendarRows.DailyTotalRow(
                        rs.getObject("transaction_date", LocalDate.class),
                        Money.scale(rs.getBigDecimal("income")),
                        Money.scale(rs.getBigDecimal("expense"))));
    }

    @Override
    public List<CalendarRows.TransactionItemRow> findCalendarTransactionItems(
            UUID familyId, UUID viewerUserId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    t.id,
                    t.transaction_date,
                    t.description,
                    c.name AS category_name,
                    t.amount,
                    t.currency_code,
                    t.status,
                    t.installment_plan_id,
                    t.installment_number,
                    ip.total_installments,
                    ip.description AS plan_description
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category_id
                LEFT JOIN installment_plans ip ON ip.id = t.installment_plan_id
                WHERE t.family_id = :familyId
                  AND t.deleted_at IS NULL
                  AND (
                      t.status = 'POSTED'
                      OR (t.status = 'PROJECTED' AND t.installment_plan_id IS NOT NULL)
                  )
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

        sql.append(" ORDER BY t.transaction_date ASC, t.created_at ASC, t.id ASC");

        return jdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> {
                    Object instNumObj = rs.getObject("installment_number");
                    Object totalInstObj = rs.getObject("total_installments");
                    return new CalendarRows.TransactionItemRow(
                            rs.getObject("id", UUID.class),
                            rs.getObject("transaction_date", LocalDate.class),
                            rs.getString("description"),
                            rs.getString("category_name"),
                            Money.scale(rs.getBigDecimal("amount")),
                            rs.getString("currency_code"),
                            rs.getString("status"),
                            rs.getObject("installment_plan_id", UUID.class),
                            instNumObj == null ? null : ((Number) instNumObj).intValue(),
                            totalInstObj == null ? null : ((Number) totalInstObj).intValue(),
                            rs.getString("plan_description"));
                });
    }

    @Override
    public List<CalendarRows.RecurringExpenseRow> findActiveRecurringExpenses(
            UUID familyId, UUID viewerUserId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    re.id,
                    re.name,
                    re.base_amount,
                    re.currency_code,
                    re.due_day,
                    re.start_date,
                    re.end_date
                FROM recurring_expenses re
                WHERE re.family_id = :familyId
                  AND re.is_active = TRUE
                  AND re.start_date <= :toDate
                  AND (re.end_date IS NULL OR re.end_date >= :fromDate)
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("fromDate", from)
                .addValue("toDate", to);

        if (viewerUserId != null) {
            sql.append(" AND re.scope = 'PERSONAL' AND re.created_by_user_id = :viewerUserId");
            params.addValue("viewerUserId", viewerUserId);
        }

        sql.append(" ORDER BY re.due_day ASC, re.name ASC");

        return jdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> new CalendarRows.RecurringExpenseRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        Money.scale(rs.getBigDecimal("base_amount")),
                        rs.getString("currency_code"),
                        rs.getInt("due_day"),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class)));
    }

    @Override
    public Set<CalendarRows.PaidRecurringKey> findPaidRecurringKeys(
            UUID familyId, LocalDate firstMonthStart, LocalDate lastMonthEnd) {
        String sql = """
                SELECT DISTINCT
                    t.recurring_expense_id,
                    EXTRACT(YEAR FROM t.transaction_date)::int AS yr,
                    EXTRACT(MONTH FROM t.transaction_date)::int AS mo
                FROM transactions t
                WHERE t.family_id = :familyId
                  AND t.deleted_at IS NULL
                  AND t.status = 'POSTED'
                  AND t.recurring_expense_id IS NOT NULL
                  AND t.transaction_date >= :fromDate
                  AND t.transaction_date <= :toDate
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("fromDate", firstMonthStart)
                .addValue("toDate", lastMonthEnd);

        Set<CalendarRows.PaidRecurringKey> paid = new HashSet<>();
        jdbcTemplate.query(sql, params, rs -> {
            UUID recurringId = rs.getObject("recurring_expense_id", UUID.class);
            YearMonth ym = YearMonth.of(rs.getInt("yr"), rs.getInt("mo"));
            paid.add(new CalendarRows.PaidRecurringKey(recurringId, ym));
        });
        return paid;
    }

    @Override
    public List<CalendarRows.VehicleDueRow> findVehicleDues(
            UUID familyId, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("fromDate", from)
                .addValue("toDate", to);

        List<CalendarRows.VehicleDueRow> dues = new ArrayList<>();

        String vehicleSql = """
                SELECT v.id, v.nickname, v.vtv_expiration_date, v.insurance_expiration
                FROM vehicles v
                WHERE v.family_id = :familyId
                  AND v.is_active = TRUE
                  AND v.deleted_at IS NULL
                  AND (
                      (v.vtv_expiration_date BETWEEN :fromDate AND :toDate)
                      OR (v.insurance_expiration BETWEEN :fromDate AND :toDate)
                  )
                ORDER BY v.nickname ASC
                """;

        jdbcTemplate.query(vehicleSql, params, rs -> {
            UUID vehicleId = rs.getObject("id", UUID.class);
            String nickname = rs.getString("nickname");
            LocalDate vtvDate = rs.getObject("vtv_expiration_date", LocalDate.class);
            LocalDate insuranceDate = rs.getObject("insurance_expiration", LocalDate.class);
            if (vtvDate != null && !vtvDate.isBefore(from) && !vtvDate.isAfter(to)) {
                dues.add(new CalendarRows.VehicleDueRow(vehicleId, vtvDate, "VTV · " + nickname));
            }
            if (insuranceDate != null
                    && !insuranceDate.isBefore(from)
                    && !insuranceDate.isAfter(to)) {
                dues.add(new CalendarRows.VehicleDueRow(
                        vehicleId, insuranceDate, "Seguro · " + nickname));
            }
        });

        String maintenanceSql = """
                SELECT latest.id, latest.vehicle_nickname, latest.description, latest.next_due_date
                FROM (
                    SELECT DISTINCT ON (ml.vehicle_id, ml.maintenance_type)
                           ml.id,
                           v.nickname AS vehicle_nickname,
                           ml.description,
                           ml.next_due_date
                    FROM maintenance_logs ml
                    JOIN vehicles v ON v.id = ml.vehicle_id
                    WHERE ml.family_id = :familyId
                      AND v.is_active = TRUE
                      AND v.deleted_at IS NULL
                    ORDER BY ml.vehicle_id, ml.maintenance_type, ml.maintenance_date DESC, ml.created_at DESC, ml.id DESC
                ) latest
                WHERE latest.next_due_date BETWEEN :fromDate AND :toDate
                ORDER BY latest.next_due_date ASC, latest.vehicle_nickname ASC
                """;

        jdbcTemplate.query(maintenanceSql, params, rs -> {
            UUID logId = rs.getObject("id", UUID.class);
            String nickname = rs.getString("vehicle_nickname");
            String description = rs.getString("description");
            LocalDate nextDueDate = rs.getObject("next_due_date", LocalDate.class);
            String title = (description != null && !description.isBlank())
                    ? nickname + " · " + description
                    : nickname + " · Mantenimiento";
            dues.add(new CalendarRows.VehicleDueRow(logId, nextDueDate, title));
        });

        return dues;
    }

    @Override
    public List<CalendarRows.AllowanceRow> findActiveAllowances(UUID familyId, UUID viewerUserId) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    a.id,
                    u.full_name AS child_full_name,
                    a.amount,
                    a.currency_code,
                    a.frequency,
                    a.day_of_period
                FROM allowances a
                JOIN users u ON u.id = a.child_user_id
                WHERE a.family_id = :familyId
                  AND a.is_active = TRUE
                """);

        MapSqlParameterSource params = new MapSqlParameterSource("familyId", familyId);
        if (viewerUserId != null) {
            sql.append(" AND a.child_user_id = :viewerUserId");
            params.addValue("viewerUserId", viewerUserId);
        }

        sql.append(" ORDER BY a.day_of_period ASC, u.full_name ASC");

        return jdbcTemplate.query(
                sql.toString(),
                params,
                (rs, rowNum) -> new CalendarRows.AllowanceRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("child_full_name"),
                        Money.scale(rs.getBigDecimal("amount")),
                        rs.getString("currency_code"),
                        rs.getString("frequency"),
                        rs.getInt("day_of_period")));
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
