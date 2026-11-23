package com.familion.finance.transactions.infrastructure.persistence;

import com.familion.common.money.Money;
import com.familion.finance.transactions.application.InvalidTransactionRequestException;
import com.familion.finance.transactions.application.TransactionFilter;
import com.familion.finance.transactions.application.TransactionReadQuery;
import com.familion.finance.transactions.application.TransactionReadRow;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcTransactionReadQuery implements TransactionReadQuery {

    private static final Map<String, String> ALLOWED_SORT_COLUMNS = Map.of(
            "transactionDate", "t.transaction_date",
            "createdAt", "t.created_at",
            "amount", "t.amount",
            "baseCurrencyAmount", "es.base_currency_amount",
            "description", "t.description");

    private static final List<String> DEFAULT_STATUSES =
            List.of(TransactionStatus.POSTED.name(), TransactionStatus.PROJECTED.name());

    static final String BASE_SELECT_SQL = """
            SELECT
                t.id,
                t.type,
                t.scope,
                t.status,
                t.amount,
                t.currency_code,
                t.transaction_date,
                t.description,
                t.notes,
                t.category_id,
                c.name AS category_name,
                c.icon AS category_icon,
                c.color_hex AS category_color_hex,
                t.account_id,
                a.name AS account_name,
                a.account_type,
                t.user_id AS paid_by_id,
                u_paid.full_name AS paid_by_full_name,
                t.attributed_to_user_id AS attributed_to_id,
                u_attr.full_name AS attributed_to_full_name,
                t.installment_plan_id,
                t.installment_number,
                ip.total_installments,
                es.reference_currency,
                es.amount_in_reference,
                es.base_currency_amount,
                es.ipc_index_value
            FROM transactions t
            JOIN accounts a ON a.id = t.account_id
            JOIN users u_paid ON u_paid.id = t.user_id
            JOIN users u_attr ON u_attr.id = t.attributed_to_user_id
            LEFT JOIN categories c ON c.id = t.category_id
            LEFT JOIN economic_snapshots es ON es.transaction_id = t.id
            LEFT JOIN installment_plans ip ON ip.id = t.installment_plan_id
            WHERE t.family_id = :familyId
              AND t.deleted_at IS NULL
            """;

    private static final String BASE_COUNT_SQL = """
            SELECT COUNT(*)
            FROM transactions t
            WHERE t.family_id = :familyId
              AND t.deleted_at IS NULL
            """;

    private static final String FIND_BY_ID_SQL = BASE_SELECT_SQL + " AND t.id = :transactionId";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTransactionReadQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TransactionReadRow> findById(UUID familyId, UUID transactionId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("transactionId", transactionId);
        List<TransactionReadRow> rows =
                jdbcTemplate.query(FIND_BY_ID_SQL, params, JdbcTransactionReadQuery::mapRow);
        return rows.stream().findFirst();
    }

    @Override
    public List<TransactionReadRow> findPage(
            UUID familyId, UUID viewerUserId, TransactionFilter filter, Pageable pageable) {
        StringBuilder sql = new StringBuilder(BASE_SELECT_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource("familyId", familyId);

        appendFilters(sql, params, viewerUserId, filter);
        sql.append(buildOrderBy(pageable.getSort()));
        sql.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        return jdbcTemplate.query(sql.toString(), params, JdbcTransactionReadQuery::mapRow);
    }

    @Override
    public long count(UUID familyId, UUID viewerUserId, TransactionFilter filter) {
        StringBuilder sql = new StringBuilder(BASE_COUNT_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource("familyId", familyId);

        appendFilters(sql, params, viewerUserId, filter);

        Long total = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return total == null ? 0L : total;
    }

    @Override
    public Map<YearMonth, BigDecimal> sumPostedIncomeByMonth(
            UUID familyId, UUID viewerUserId, Set<YearMonth> months) {
        if (months == null || months.isEmpty()) {
            return Map.of();
        }

        List<LocalDate> monthStarts =
                months.stream().map(ym -> ym.atDay(1)).sorted().toList();

        StringBuilder sql = new StringBuilder("""
                SELECT
                    EXTRACT(YEAR FROM t.transaction_date)::int AS yr,
                    EXTRACT(MONTH FROM t.transaction_date)::int AS mo,
                    COALESCE(SUM(es.base_currency_amount), 0) AS total_income
                FROM transactions t
                JOIN economic_snapshots es ON es.transaction_id = t.id
                WHERE t.family_id = :familyId
                  AND t.deleted_at IS NULL
                  AND t.status = 'POSTED'
                  AND t.type = 'INCOME'
                  AND DATE_TRUNC('month', t.transaction_date)::date IN (:monthStarts)
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("monthStarts", monthStarts);

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

    private static void appendFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            UUID viewerUserId,
            TransactionFilter filter) {
        if (viewerUserId != null) {
            sql.append(" AND t.attributed_to_user_id = :viewerUserId");
            params.addValue("viewerUserId", viewerUserId);
        }

        List<String> statuses = (filter.statuses() == null || filter.statuses().isEmpty())
                ? DEFAULT_STATUSES
                : filter.statuses().stream().map(Enum::name).toList();
        sql.append(" AND t.status IN (:statuses)");
        params.addValue("statuses", statuses);

        if (filter.from() != null) {
            sql.append(" AND t.transaction_date >= :fromDate");
            params.addValue("fromDate", filter.from());
        }
        if (filter.to() != null) {
            sql.append(" AND t.transaction_date <= :toDate");
            params.addValue("toDate", filter.to());
        }
        if (filter.types() != null && !filter.types().isEmpty()) {
            sql.append(" AND t.type IN (:types)");
            params.addValue("types", filter.types().stream().map(Enum::name).toList());
        }
        if (filter.categoryId() != null) {
            sql.append(" AND t.category_id = :categoryId");
            params.addValue("categoryId", filter.categoryId());
        }
        if (filter.accountId() != null) {
            sql.append(" AND t.account_id = :accountId");
            params.addValue("accountId", filter.accountId());
        }
        if (filter.attributedToUserId() != null) {
            sql.append(" AND t.attributed_to_user_id = :attributedToUserId");
            params.addValue("attributedToUserId", filter.attributedToUserId());
        }
        if (filter.currency() != null && !filter.currency().isBlank()) {
            sql.append(" AND t.currency_code = :currency");
            params.addValue("currency", filter.currency().trim().toUpperCase(Locale.ROOT));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            String escaped = filter.q()
                    .trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            sql.append(" AND t.description ILIKE :qPattern ESCAPE '\\'");
            params.addValue("qPattern", "%" + escaped + "%");
        }
    }

    private static String buildOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return " ORDER BY t.transaction_date DESC, t.created_at DESC, t.id DESC";
        }
        List<String> clauses = new ArrayList<>();
        boolean hasCreatedAt = false;
        for (Sort.Order order : sort) {
            String column = ALLOWED_SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                throw new InvalidTransactionRequestException(
                        "sort", "Unsupported sort field: " + order.getProperty());
            }
            if ("createdAt".equals(order.getProperty())) {
                hasCreatedAt = true;
            }
            String direction = order.isAscending() ? "ASC" : "DESC";
            clauses.add(column + " " + direction);
        }
        if (!hasCreatedAt) {
            clauses.add("t.created_at DESC");
        }
        clauses.add("t.id DESC");
        return " ORDER BY " + String.join(", ", clauses);
    }

    static TransactionReadRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TransactionReadRow(
                rs.getObject("id", UUID.class),
                TransactionType.valueOf(rs.getString("type")),
                Scope.valueOf(rs.getString("scope")),
                TransactionStatus.valueOf(rs.getString("status")),
                Money.scale(rs.getBigDecimal("amount")),
                rs.getString("currency_code"),
                rs.getObject("transaction_date", LocalDate.class),
                rs.getString("description"),
                rs.getString("notes"),
                rs.getObject("category_id", UUID.class),
                rs.getString("category_name"),
                rs.getString("category_icon"),
                rs.getString("category_color_hex"),
                rs.getObject("account_id", UUID.class),
                rs.getString("account_name"),
                rs.getString("account_type"),
                rs.getObject("paid_by_id", UUID.class),
                rs.getString("paid_by_full_name"),
                rs.getObject("attributed_to_id", UUID.class),
                rs.getString("attributed_to_full_name"),
                rs.getObject("installment_plan_id", UUID.class),
                getNullableInt(rs, "installment_number"),
                getNullableInt(rs, "total_installments"),
                rs.getString("reference_currency"),
                getNullableScaledMoney(rs, "amount_in_reference"),
                getNullableScaledMoney(rs, "base_currency_amount"),
                getNullableRate(rs, "ipc_index_value"));
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static BigDecimal getNullableScaledMoney(ResultSet rs, String column)
            throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : Money.scale(value);
    }

    private static BigDecimal getNullableRate(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : Money.rate(value);
    }
}
