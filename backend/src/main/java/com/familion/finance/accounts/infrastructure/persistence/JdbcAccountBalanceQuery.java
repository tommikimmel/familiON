package com.familion.finance.accounts.infrastructure.persistence;

import com.familion.common.money.Money;
import com.familion.finance.accounts.application.AccountBalanceQuery;
import com.familion.finance.accounts.application.AccountListingScope;
import com.familion.finance.accounts.application.AccountView;
import com.familion.finance.accounts.domain.AccountType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcAccountBalanceQuery implements AccountBalanceQuery {

    private static final String SINGLE_BALANCE_SQL = """
            SELECT
                a.initial_balance + COALESCE(
                    SUM(
                        CASE
                            WHEN t.type IN ('INCOME', 'TRANSFER_IN', 'EXCHANGE_IN', 'ALLOWANCE_IN') THEN t.amount
                            WHEN t.type IN ('EXPENSE', 'TRANSFER_OUT', 'EXCHANGE_OUT', 'ALLOWANCE_OUT') THEN -t.amount
                            ELSE 0
                        END
                    ),
                    0
                ) AS current_balance
            FROM accounts a
            LEFT JOIN transactions t
                ON t.account_id = a.id
               AND t.status = 'POSTED'
               AND t.deleted_at IS NULL
            WHERE a.id = :accountId
            GROUP BY a.id, a.initial_balance
            """;

    private static final String LIST_BASE_SQL = """
            SELECT
                a.id,
                a.name,
                a.account_type,
                a.currency_code,
                a.initial_balance,
                a.initial_balance + COALESCE(tx.balance_delta, 0) AS current_balance,
                a.owner_user_id,
                a.credit_limit,
                a.closing_day,
                a.due_day,
                a.is_active
            FROM accounts a
            LEFT JOIN (
                SELECT
                    t.account_id,
                    SUM(
                        CASE
                            WHEN t.type IN ('INCOME', 'TRANSFER_IN', 'EXCHANGE_IN', 'ALLOWANCE_IN') THEN t.amount
                            WHEN t.type IN ('EXPENSE', 'TRANSFER_OUT', 'EXCHANGE_OUT', 'ALLOWANCE_OUT') THEN -t.amount
                            ELSE 0
                        END
                    ) AS balance_delta
                FROM transactions t
                WHERE t.family_id = :familyId
                  AND t.status = 'POSTED'
                  AND t.deleted_at IS NULL
                GROUP BY t.account_id
            ) tx ON tx.account_id = a.id
            WHERE a.family_id = :familyId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAccountBalanceQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AccountView> listWithBalances(
            UUID familyId, UUID userId, AccountListingScope scope, boolean includeInactive) {
        StringBuilder sql = new StringBuilder(LIST_BASE_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource("familyId", familyId);

        switch (scope) {
            case OWN_ONLY -> {
                sql.append(" AND a.owner_user_id = :userId");
                params.addValue("userId", userId);
            }
            case OWN_AND_SHARED -> {
                sql.append(" AND (a.owner_user_id = :userId OR a.owner_user_id IS NULL)");
                params.addValue("userId", userId);
            }
            case ALL_FAMILY -> {
                // No additional owner filter: all accounts in the family
            }
        }

        if (!includeInactive) {
            sql.append(" AND a.is_active = TRUE AND a.deleted_at IS NULL");
        }

        sql.append(" ORDER BY LOWER(a.name) ASC, a.id ASC");

        return jdbcTemplate.query(sql.toString(), params, JdbcAccountBalanceQuery::mapRow);
    }

    @Override
    public Optional<BigDecimal> computeCurrentBalance(UUID accountId) {
        List<BigDecimal> rows = jdbcTemplate.query(
                SINGLE_BALANCE_SQL,
                new MapSqlParameterSource("accountId", accountId),
                (rs, rowNum) -> Money.scale(rs.getBigDecimal("current_balance")));
        return rows.stream().findFirst();
    }

    private static AccountView mapRow(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal creditLimit = rs.getBigDecimal("credit_limit");
        return new AccountView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                AccountType.valueOf(rs.getString("account_type")),
                rs.getString("currency_code"),
                Money.scale(rs.getBigDecimal("initial_balance")),
                Money.scale(rs.getBigDecimal("current_balance")),
                rs.getObject("owner_user_id", UUID.class),
                creditLimit == null ? null : Money.scale(creditLimit),
                getNullableInt(rs, "closing_day"),
                getNullableInt(rs, "due_day"),
                rs.getBoolean("is_active"));
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }
}
