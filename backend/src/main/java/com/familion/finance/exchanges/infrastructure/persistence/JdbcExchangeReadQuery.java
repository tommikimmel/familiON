package com.familion.finance.exchanges.infrastructure.persistence;

import com.familion.common.money.Money;
import com.familion.finance.exchanges.application.ExchangePositionRow;
import com.familion.finance.exchanges.application.ExchangeReadQuery;
import com.familion.finance.exchanges.application.ExchangeReadRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcExchangeReadQuery implements ExchangeReadQuery {

    private static final String ACTIVE_EXCHANGE_IN_EXISTS = """
            EXISTS (
                SELECT 1
                FROM transactions t
                WHERE t.currency_exchange_id = ce.id
                  AND t.family_id = ce.family_id
                  AND t.type = 'EXCHANGE_IN'
                  AND t.deleted_at IS NULL
            )
            """;

    private static final String BASE_SELECT_SQL = """
            SELECT
                ce.id,
                ce.exchange_date,
                ce.source_account_id,
                sa.name AS source_account_name,
                ce.target_account_id,
                ta.name AS target_account_name,
                ce.source_amount,
                ce.source_currency,
                ce.target_amount,
                ce.target_currency,
                ce.applied_exchange_rate,
                ce.fee_amount,
                ce.fee_currency,
                ce.notes,
                ce.user_id AS created_by_id,
                u.full_name AS created_by_full_name
            FROM currency_exchanges ce
            JOIN accounts sa ON sa.id = ce.source_account_id
            JOIN accounts ta ON ta.id = ce.target_account_id
            JOIN users u ON u.id = ce.user_id
            WHERE ce.family_id = :familyId
              AND """ + " " + ACTIVE_EXCHANGE_IN_EXISTS;

    private static final String BASE_COUNT_SQL = """
            SELECT COUNT(*)
            FROM currency_exchanges ce
            WHERE ce.family_id = :familyId
              AND """ + " " + ACTIVE_EXCHANGE_IN_EXISTS;

    private static final String BASE_POSITION_SQL = """
            SELECT
                ce.source_currency,
                ce.source_amount,
                ce.target_currency,
                ce.target_amount
            FROM currency_exchanges ce
            WHERE ce.family_id = :familyId
              AND """ + " " + ACTIVE_EXCHANGE_IN_EXISTS;

    private static final String HAS_ACTIVE_EXCHANGE_IN_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM transactions t
                WHERE t.family_id = :familyId
                  AND t.currency_exchange_id = :exchangeId
                  AND t.type = 'EXCHANGE_IN'
                  AND t.deleted_at IS NULL
            )
            """;

    private static final String FIND_ACTIVE_TRANSACTION_IDS_SQL = """
            SELECT t.id
            FROM transactions t
            WHERE t.family_id = :familyId
              AND t.currency_exchange_id = :exchangeId
              AND t.deleted_at IS NULL
            ORDER BY t.created_at ASC, t.id ASC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcExchangeReadQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ExchangeReadRow> findPage(UUID familyId, UUID viewerUserId, Pageable pageable) {
        StringBuilder sql = new StringBuilder(BASE_SELECT_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource("familyId", familyId);

        if (viewerUserId != null) {
            sql.append(" AND ce.user_id = :viewerUserId");
            params.addValue("viewerUserId", viewerUserId);
        }

        sql.append(" ORDER BY ce.exchange_date DESC, ce.created_at DESC, ce.id DESC");
        sql.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        return jdbcTemplate.query(sql.toString(), params, JdbcExchangeReadQuery::mapRow);
    }

    @Override
    public long count(UUID familyId, UUID viewerUserId) {
        StringBuilder sql = new StringBuilder(BASE_COUNT_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource("familyId", familyId);

        if (viewerUserId != null) {
            sql.append(" AND ce.user_id = :viewerUserId");
            params.addValue("viewerUserId", viewerUserId);
        }

        Long total = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return total != null ? total : 0L;
    }

    @Override
    public List<ExchangePositionRow> findAllActiveChronological(UUID familyId, UUID viewerUserId) {
        StringBuilder sql = new StringBuilder(BASE_POSITION_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource("familyId", familyId);

        if (viewerUserId != null) {
            sql.append(" AND ce.user_id = :viewerUserId");
            params.addValue("viewerUserId", viewerUserId);
        }

        sql.append(" ORDER BY ce.exchange_date ASC, ce.created_at ASC, ce.id ASC");

        return jdbcTemplate.query(sql.toString(), params, JdbcExchangeReadQuery::mapPositionRow);
    }

    @Override
    public boolean hasActiveExchangeIn(UUID familyId, UUID exchangeId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("exchangeId", exchangeId);
        Boolean exists = jdbcTemplate.queryForObject(HAS_ACTIVE_EXCHANGE_IN_SQL, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public List<UUID> findActiveTransactionIds(UUID familyId, UUID exchangeId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("exchangeId", exchangeId);
        return jdbcTemplate.query(
                FIND_ACTIVE_TRANSACTION_IDS_SQL, params, (rs, rowNum) -> rs.getObject("id", UUID.class));
    }

    private static ExchangeReadRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ExchangeReadRow(
                rs.getObject("id", UUID.class),
                rs.getObject("exchange_date", LocalDate.class),
                rs.getObject("source_account_id", UUID.class),
                rs.getString("source_account_name"),
                rs.getObject("target_account_id", UUID.class),
                rs.getString("target_account_name"),
                Money.scale(rs.getBigDecimal("source_amount")),
                rs.getString("source_currency"),
                Money.scale(rs.getBigDecimal("target_amount")),
                rs.getString("target_currency"),
                Money.rate(rs.getBigDecimal("applied_exchange_rate")),
                Money.scale(rs.getBigDecimal("fee_amount")),
                rs.getString("fee_currency"),
                rs.getString("notes"),
                rs.getObject("created_by_id", UUID.class),
                rs.getString("created_by_full_name"));
    }

    private static ExchangePositionRow mapPositionRow(ResultSet rs, int rowNum) throws SQLException {
        return new ExchangePositionRow(
                rs.getString("source_currency"),
                Money.scale(rs.getBigDecimal("source_amount")),
                rs.getString("target_currency"),
                Money.scale(rs.getBigDecimal("target_amount")));
    }
}
