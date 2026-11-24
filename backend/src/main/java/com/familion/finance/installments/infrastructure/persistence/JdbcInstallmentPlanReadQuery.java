package com.familion.finance.installments.infrastructure.persistence;

import com.familion.common.money.Money;
import com.familion.finance.installments.application.InstallmentPlanReadQuery;
import com.familion.finance.installments.application.InstallmentPlanReadRow;
import com.familion.finance.installments.application.InstallmentTransactionRow;
import com.familion.finance.installments.domain.InstallmentPlanStatus;
import com.familion.finance.transactions.application.api.TransactionStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcInstallmentPlanReadQuery implements InstallmentPlanReadQuery {

    private static final String BASE_PLAN_SELECT = """
            SELECT
                ip.id,
                ip.family_id,
                ip.user_id,
                ip.description,
                ip.category_id,
                c.name AS category_name,
                c.icon AS category_icon,
                c.color_hex AS category_color_hex,
                ip.credit_card_account_id,
                a.name AS credit_card_account_name,
                a.closing_day AS credit_card_closing_day,
                a.due_day AS credit_card_due_day,
                ip.currency_code,
                ip.total_installments,
                ip.has_interest,
                ip.cash_price,
                ip.financed_total,
                ip.installment_amount,
                ip.purchase_date,
                ip.first_billing_date,
                ip.status,
                u.full_name AS creator_full_name
            FROM installment_plans ip
            JOIN categories c ON c.id = ip.category_id
            JOIN accounts a ON a.id = ip.credit_card_account_id
            JOIN users u ON u.id = ip.user_id
            WHERE ip.family_id = :familyId
            """;

    private static final String FIND_INSTALLMENTS_BY_PLAN_IDS_SQL = """
            SELECT
                t.id AS transaction_id,
                t.installment_plan_id,
                t.installment_number,
                t.transaction_date,
                t.amount,
                t.status,
                t.attributed_to_user_id,
                u.full_name AS attributed_to_full_name
            FROM transactions t
            JOIN users u ON u.id = t.attributed_to_user_id
            WHERE t.family_id = :familyId
              AND t.installment_plan_id IN (:planIds)
              AND t.deleted_at IS NULL
            ORDER BY t.installment_plan_id ASC, t.installment_number ASC, t.transaction_date ASC, t.id ASC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcInstallmentPlanReadQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<InstallmentPlanReadRow> findPlans(
            UUID familyId, UUID viewerUserId, InstallmentPlanStatus status) {
        StringBuilder sql = new StringBuilder(BASE_PLAN_SELECT);
        MapSqlParameterSource params = new MapSqlParameterSource("familyId", familyId);

        if (status != null) {
            sql.append(" AND ip.status = :status");
            params.addValue("status", status.name());
        }
        if (viewerUserId != null) {
            sql.append("""
                     AND EXISTS (
                        SELECT 1
                        FROM transactions t
                        WHERE t.installment_plan_id = ip.id
                          AND t.family_id = ip.family_id
                          AND t.deleted_at IS NULL
                          AND t.attributed_to_user_id = :viewerUserId
                    )
                    """);
            params.addValue("viewerUserId", viewerUserId);
        }
        sql.append(" ORDER BY ip.purchase_date DESC, ip.created_at DESC, ip.id DESC");

        return jdbcTemplate.query(sql.toString(), params, JdbcInstallmentPlanReadQuery::mapPlanRow);
    }

    @Override
    public Optional<InstallmentPlanReadRow> findPlanById(UUID familyId, UUID planId) {
        String sql = BASE_PLAN_SELECT + " AND ip.id = :planId";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("planId", planId);
        List<InstallmentPlanReadRow> rows =
                jdbcTemplate.query(sql, params, JdbcInstallmentPlanReadQuery::mapPlanRow);
        return rows.stream().findFirst();
    }

    @Override
    public List<InstallmentTransactionRow> findInstallmentsByPlanIds(
            UUID familyId, Collection<UUID> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("planIds", planIds);
        return jdbcTemplate.query(
                FIND_INSTALLMENTS_BY_PLAN_IDS_SQL,
                params,
                JdbcInstallmentPlanReadQuery::mapInstallmentRow);
    }

    private static InstallmentPlanReadRow mapPlanRow(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal cashPrice = rs.getBigDecimal("cash_price");
        return new InstallmentPlanReadRow(
                rs.getObject("id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("description"),
                rs.getObject("category_id", UUID.class),
                rs.getString("category_name"),
                rs.getString("category_icon"),
                rs.getString("category_color_hex"),
                rs.getObject("credit_card_account_id", UUID.class),
                rs.getString("credit_card_account_name"),
                getNullableInt(rs, "credit_card_closing_day"),
                getNullableInt(rs, "credit_card_due_day"),
                rs.getString("currency_code"),
                rs.getInt("total_installments"),
                rs.getBoolean("has_interest"),
                cashPrice == null ? null : Money.scale(cashPrice),
                Money.scale(rs.getBigDecimal("financed_total")),
                Money.scale(rs.getBigDecimal("installment_amount")),
                rs.getObject("purchase_date", LocalDate.class),
                rs.getObject("first_billing_date", LocalDate.class),
                InstallmentPlanStatus.valueOf(rs.getString("status")),
                rs.getString("creator_full_name"));
    }

    private static InstallmentTransactionRow mapInstallmentRow(ResultSet rs, int rowNum)
            throws SQLException {
        return new InstallmentTransactionRow(
                rs.getObject("transaction_id", UUID.class),
                rs.getObject("installment_plan_id", UUID.class),
                rs.getInt("installment_number"),
                rs.getObject("transaction_date", LocalDate.class),
                Money.scale(rs.getBigDecimal("amount")),
                TransactionStatus.valueOf(rs.getString("status")),
                rs.getObject("attributed_to_user_id", UUID.class),
                rs.getString("attributed_to_full_name"));
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }
}
