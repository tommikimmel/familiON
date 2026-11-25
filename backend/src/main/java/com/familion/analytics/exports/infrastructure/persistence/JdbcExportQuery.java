package com.familion.analytics.exports.infrastructure.persistence;

import com.familion.analytics.exports.application.ExportQuery;
import com.familion.analytics.exports.application.ExportTransactionRow;
import com.familion.common.money.Money;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcExportQuery implements ExportQuery {

    private static final List<String> PERSONAL_INCOME_TYPES = List.of("INCOME", "ALLOWANCE_IN");
    private static final List<String> FAMILY_INCOME_TYPES = List.of("INCOME");

    private static final String TRANSACTIONS_PAGE_SQL = """
            SELECT
                t.transaction_date,
                t.type,
                t.description,
                c.name AS category_name,
                a.name AS account_name,
                u_paid.full_name AS paid_by_full_name,
                u_attr.full_name AS attributed_to_full_name,
                t.currency_code,
                t.amount,
                es.base_currency_amount,
                es.amount_in_reference,
                es.ipc_index_value,
                t.installment_number,
                ip.total_installments,
                t.status
            FROM transactions t
            JOIN accounts a ON a.id = t.account_id
            JOIN users u_paid ON u_paid.id = t.user_id
            JOIN users u_attr ON u_attr.id = t.attributed_to_user_id
            LEFT JOIN categories c ON c.id = t.category_id
            LEFT JOIN economic_snapshots es ON es.transaction_id = t.id
            LEFT JOIN installment_plans ip ON ip.id = t.installment_plan_id
            WHERE t.family_id = :familyId
              AND t.deleted_at IS NULL
              AND t.status IN ('POSTED', 'PROJECTED')
              AND t.transaction_date >= :fromDate
              AND t.transaction_date <= :toDate
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcExportQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ExportTransactionRow> findTransactionPage(
            UUID familyId,
            UUID viewerUserId,
            LocalDate from,
            LocalDate to,
            int limit,
            long offset) {
        StringBuilder sql = new StringBuilder(TRANSACTIONS_PAGE_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("fromDate", from)
                .addValue("toDate", to)
                .addValue("limit", limit)
                .addValue("offset", offset);

        if (viewerUserId != null) {
            sql.append(" AND t.attributed_to_user_id = :viewerUserId");
            params.addValue("viewerUserId", viewerUserId);
        }

        sql.append(" ORDER BY t.transaction_date DESC, t.created_at DESC, t.id DESC");
        sql.append(" LIMIT :limit OFFSET :offset");

        return jdbcTemplate.query(sql.toString(), params, JdbcExportQuery::mapRow);
    }

    @Override
    public Map<YearMonth, BigDecimal> sumPostedIncomeByMonth(
            UUID familyId,
            UUID viewerUserId,
            boolean includeAllowanceIn,
            LocalDate fromMonthStart,
            LocalDate toMonthEnd) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    EXTRACT(YEAR FROM t.transaction_date)::int AS yr,
                    EXTRACT(MONTH FROM t.transaction_date)::int AS mo,
                    COALESCE(SUM(COALESCE(es.base_currency_amount, t.amount)), 0) AS total_income
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
                .addValue("fromDate", fromMonthStart)
                .addValue("toDate", toMonthEnd);

        if (viewerUserId != null) {
            sql.append(" AND t.attributed_to_user_id = :viewerUserId");
            params.addValue("viewerUserId", viewerUserId);
        }

        sql.append(" GROUP BY yr, mo");

        Map<YearMonth, BigDecimal> totals = new HashMap<>();
        jdbcTemplate.query(sql.toString(), params, rs -> {
            int year = rs.getInt("yr");
            int month = rs.getInt("mo");
            BigDecimal total = rs.getBigDecimal("total_income");
            totals.put(YearMonth.of(year, month), Money.scale(total));
        });
        return totals;
    }

    private static ExportTransactionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ExportTransactionRow(
                rs.getObject("transaction_date", LocalDate.class),
                TransactionType.valueOf(rs.getString("type")),
                rs.getString("description"),
                rs.getString("category_name"),
                rs.getString("account_name"),
                rs.getString("paid_by_full_name"),
                rs.getString("attributed_to_full_name"),
                rs.getString("currency_code"),
                Money.scale(rs.getBigDecimal("amount")),
                getNullableMoney(rs, "base_currency_amount"),
                getNullableMoney(rs, "amount_in_reference"),
                getNullableRate(rs, "ipc_index_value"),
                getNullableInt(rs, "installment_number"),
                getNullableInt(rs, "total_installments"),
                TransactionStatus.valueOf(rs.getString("status")));
    }

    private static BigDecimal getNullableMoney(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : Money.scale(value);
    }

    private static BigDecimal getNullableRate(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : Money.rate(value);
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }
}
