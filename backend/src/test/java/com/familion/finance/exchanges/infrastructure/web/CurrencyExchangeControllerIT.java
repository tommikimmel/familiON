package com.familion.finance.exchanges.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-"
        })
class CurrencyExchangeControllerIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AccountQueryApi accountQueryApi;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM economic_indices");
        Set<String> econKeys = redisTemplate.keys("econ:*");
        if (econKeys != null && !econKeys.isEmpty()) {
            redisTemplate.delete(econKeys);
        }
    }

    @Test
    void buyUsdCreatesMirroredTransactionsWithSharedGroupAndUpdatesAccountBalances() {
        insertIndex("USD_MEP", LocalDate.of(2026, 11, 1), new BigDecimal("1200.000000"));

        UUID familyId = fixtures.createFamily("Familia Exchange Buy " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-buy-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID arsAccountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID usdAccountId = fixtures.createAccount(familyId, adultId, "BANK", "USD");
        jdbcTemplate.update(
                "UPDATE accounts SET initial_balance = 2000000.0000 WHERE id = ?", arsAccountId);

        String bearer = tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/currency-exchanges")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sourceAccountId", arsAccountId.toString(),
                        "targetAccountId", usdAccountId.toString(),
                        "sourceAmount", new BigDecimal("1185000"),
                        "targetAmount", new BigDecimal("1000"),
                        "exchangeDate", "2026-11-15",
                        "notes", "Compra USD MEP"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = resp.getBody();
        assertThat(body).isNotNull();
        UUID exchangeId = UUID.fromString(body.path("id").asText());
        assertThat(body.path("exchangeDate").asText()).isEqualTo("2026-11-15");
        assertThat(body.path("sourceAccount").path("id").asText()).isEqualTo(arsAccountId.toString());
        assertThat(body.path("targetAccount").path("id").asText()).isEqualTo(usdAccountId.toString());
        assertThat(body.path("sourceAmount").asText()).isEqualTo("1185000.0000");
        assertThat(body.path("sourceCurrency").asText()).isEqualTo("ARS");
        assertThat(body.path("targetAmount").asText()).isEqualTo("1000.0000");
        assertThat(body.path("targetCurrency").asText()).isEqualTo("USD");
        assertThat(body.path("appliedExchangeRate").asText()).isEqualTo("1185.000000");
        assertThat(body.path("feeAmount").asText()).isEqualTo("0.0000");
        assertThat(body.path("feeCurrency").isNull()).isTrue();
        assertThat(body.path("notes").asText()).isEqualTo("Compra USD MEP");
        assertThat(body.path("operation").asText()).isEqualTo("BUY");
        assertThat(body.path("createdBy").path("id").asText()).isEqualTo(adultId.toString());

        List<Map<String, Object>> txRows = jdbcTemplate.queryForList(
                """
                SELECT type, account_id, amount, currency_code, scope, category_id,
                       attributed_to_user_id, transfer_group_id, deleted_at
                FROM transactions
                WHERE currency_exchange_id = ?
                ORDER BY type ASC
                """,
                exchangeId);
        assertThat(txRows).hasSize(2);

        Map<String, Object> inRow = txRows.get(0);
        Map<String, Object> outRow = txRows.get(1);
        assertThat(inRow.get("type")).isEqualTo("EXCHANGE_IN");
        assertThat(inRow.get("account_id")).isEqualTo(usdAccountId);
        assertThat((BigDecimal) inRow.get("amount")).isEqualByComparingTo("1000.0000");
        assertThat(inRow.get("currency_code")).isEqualTo("USD");
        assertThat(inRow.get("scope")).isEqualTo("PERSONAL");
        assertThat(inRow.get("category_id")).isNull();
        assertThat(inRow.get("attributed_to_user_id")).isEqualTo(adultId);

        assertThat(outRow.get("type")).isEqualTo("EXCHANGE_OUT");
        assertThat(outRow.get("account_id")).isEqualTo(arsAccountId);
        assertThat((BigDecimal) outRow.get("amount")).isEqualByComparingTo("1185000.0000");
        assertThat(outRow.get("currency_code")).isEqualTo("ARS");
        assertThat(outRow.get("scope")).isEqualTo("PERSONAL");
        assertThat(outRow.get("category_id")).isNull();
        assertThat(outRow.get("transfer_group_id")).isNotNull();
        assertThat(inRow.get("transfer_group_id")).isEqualTo(outRow.get("transfer_group_id"));

        assertThat(accountQueryApi.currentBalance(arsAccountId))
                .isEqualByComparingTo("815000.0000");
        assertThat(accountQueryApi.currentBalance(usdAccountId))
                .isEqualByComparingTo("1000.0000");
    }

    @Test
    void feeCreatesExpenseInFinancialCostsCategoryAndDeductsFromSourceAccount() {
        UUID familyId = fixtures.createFamily("Familia Fee " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-fee-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID arsAccountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID usdAccountId = fixtures.createAccount(familyId, adultId, "BANK", "USD");
        jdbcTemplate.update(
                "UPDATE accounts SET initial_balance = 1500000.0000 WHERE id = ?", arsAccountId);

        String bearer = tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/currency-exchanges")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sourceAccountId", arsAccountId.toString(),
                        "targetAccountId", usdAccountId.toString(),
                        "sourceAmount", new BigDecimal("1185000"),
                        "targetAmount", new BigDecimal("1000"),
                        "feeAmount", new BigDecimal("7500"),
                        "exchangeDate", "2026-11-16"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = resp.getBody();
        assertThat(body).isNotNull();
        UUID exchangeId = UUID.fromString(body.path("id").asText());
        assertThat(body.path("feeAmount").asText()).isEqualTo("7500.0000");
        assertThat(body.path("feeCurrency").asText()).isEqualTo("ARS");

        Map<String, Object> feeTx = jdbcTemplate.queryForMap(
                """
                SELECT t.amount, t.currency_code, t.account_id, c.system_key
                FROM transactions t
                JOIN categories c ON c.id = t.category_id
                WHERE t.currency_exchange_id = ?
                  AND t.type = 'EXPENSE'
                """,
                exchangeId);
        assertThat((BigDecimal) feeTx.get("amount")).isEqualByComparingTo("7500.0000");
        assertThat(feeTx.get("currency_code")).isEqualTo("ARS");
        assertThat(feeTx.get("account_id")).isEqualTo(arsAccountId);
        assertThat(feeTx.get("system_key")).isEqualTo("FINANCIAL_COSTS");

        // 1,500,000 - 1,185,000 - 7,500 = 307,500
        assertThat(accountQueryApi.currentBalance(arsAccountId))
                .isEqualByComparingTo("307500.0000");
    }

    @Test
    void rejectsInconsistentAppliedExchangeRateAndSameCurrencyWith422() {
        UUID familyId = fixtures.createFamily("Familia Validation " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-val-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID arsAccount1 = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID arsAccount2 = fixtures.createAccount(familyId, adultId, "CASH", "ARS");
        UUID usdAccount = fixtures.createAccount(familyId, adultId, "BANK", "USD");

        String bearer = tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        ResponseEntity<JsonNode> badRateResp = client()
                .post()
                .uri("/api/v1/currency-exchanges")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sourceAccountId", arsAccount1.toString(),
                        "targetAccountId", usdAccount.toString(),
                        "sourceAmount", new BigDecimal("1185000"),
                        "targetAmount", new BigDecimal("1000"),
                        "appliedExchangeRate", new BigDecimal("1300.000000")))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(badRateResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<JsonNode> sameCurrencyResp = client()
                .post()
                .uri("/api/v1/currency-exchanges")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sourceAccountId", arsAccount1.toString(),
                        "targetAccountId", arsAccount2.toString(),
                        "sourceAmount", new BigDecimal("50000"),
                        "targetAmount", new BigDecimal("50000")))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(sameCurrencyResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void listAndDeleteEnforceFamilyIsolationVisibilityAndSoftDeleteSemantics() {
        UUID familyA = fixtures.createFamily("Familia A " + UUID.randomUUID());
        UUID familyB = fixtures.createFamily("Familia B " + UUID.randomUUID());

        UUID adultA = fixtures.createUser(
                familyA,
                "adult-a-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childA = fixtures.createUser(
                familyA,
                "child-a-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        UUID adultB = fixtures.createUser(
                familyB,
                "adult-b-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);

        UUID arsA = fixtures.createAccount(familyA, adultA, "BANK", "ARS");
        UUID usdA = fixtures.createAccount(familyA, adultA, "BANK", "USD");

        String adultABearer =
                tokens.bearer(adultA, familyA, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childABearer =
                tokens.bearer(childA, familyA, Role.HIJO, FamilyVisibility.OWN_ONLY);
        String adultBBearer =
                tokens.bearer(adultB, familyB, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        ResponseEntity<JsonNode> created = client()
                .post()
                .uri("/api/v1/currency-exchanges")
                .header(HttpHeaders.AUTHORIZATION, adultABearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sourceAccountId", arsA.toString(),
                        "targetAccountId", usdA.toString(),
                        "sourceAmount", new BigDecimal("1185000"),
                        "targetAmount", new BigDecimal("1000"),
                        "feeAmount", new BigDecimal("1000"),
                        "exchangeDate", "2026-11-18"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID exchangeId = UUID.fromString(created.getBody().path("id").asText());

        // HIJO OWN_ONLY with view=FAMILY -> 403
        ResponseEntity<JsonNode> childFamilyList = client()
                .get()
                .uri("/api/v1/currency-exchanges?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, childABearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childFamilyList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Other family deleting exchange -> 404
        ResponseEntity<JsonNode> otherFamilyDelete = client()
                .delete()
                .uri("/api/v1/currency-exchanges/" + exchangeId)
                .header(HttpHeaders.AUTHORIZATION, adultBBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(otherFamilyDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Child deleting adult's exchange -> 403
        ResponseEntity<JsonNode> childDelete = client()
                .delete()
                .uri("/api/v1/currency-exchanges/" + exchangeId)
                .header(HttpHeaders.AUTHORIZATION, childABearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Adult listing sees the exchange
        ResponseEntity<JsonNode> listBeforeDelete = client()
                .get()
                .uri("/api/v1/currency-exchanges?view=FAMILY&page=0&size=10")
                .header(HttpHeaders.AUTHORIZATION, adultABearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(listBeforeDelete.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listBeforeDelete.getBody().path("content")).hasSize(1);

        // Adult deletes exchange -> 204
        ResponseEntity<Void> deleteResp = client()
                .delete()
                .uri("/api/v1/currency-exchanges/" + exchangeId)
                .header(HttpHeaders.AUTHORIZATION, adultABearer)
                .retrieve()
                .toBodilessEntity();
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Historical row remains in currency_exchanges, but all linked transactions are soft-deleted
        Integer exchangeRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM currency_exchanges WHERE id = ?", Integer.class, exchangeId);
        Integer activeTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE currency_exchange_id = ? AND deleted_at IS NULL",
                Integer.class,
                exchangeId);
        assertThat(exchangeRowCount).isEqualTo(1);
        assertThat(activeTxCount).isZero();

        // Excluded from listings and balances restored to 0
        ResponseEntity<JsonNode> listAfterDelete = client()
                .get()
                .uri("/api/v1/currency-exchanges?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, adultABearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(listAfterDelete.getBody().path("content")).isEmpty();
        assertThat(accountQueryApi.currentBalance(arsA)).isEqualByComparingTo("0.0000");
        assertThat(accountQueryApi.currentBalance(usdA)).isEqualByComparingTo("0.0000");

        // Second delete -> 404
        ResponseEntity<JsonNode> secondDelete = client()
                .delete()
                .uri("/api/v1/currency-exchanges/" + exchangeId)
                .header(HttpHeaders.AUTHORIZATION, adultABearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(secondDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void positionsComputeWeightedAverageCostAfterBuyAndSellAndExcludeDeletedExchanges() {
        insertIndex("USD_MEP", LocalDate.of(2026, 11, 20), new BigDecimal("1250.000000"));
        insertIndex("EUR_OFICIAL", LocalDate.of(2026, 11, 20), new BigDecimal("1400.000000"));

        UUID familyId = fixtures.createFamily("Familia Positions " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-pos-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childId = fixtures.createUser(
                familyId,
                "child-pos-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);

        UUID arsAccountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID usdAccountId = fixtures.createAccount(familyId, adultId, "BANK", "USD");
        UUID eurAccountId = fixtures.createAccount(familyId, adultId, "BANK", "EUR");

        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childBearer =
                tokens.bearer(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        // 1. Buy 1000 USD for 1,185,000 ARS -> rate 1185.000000
        ResponseEntity<JsonNode> buyResp = client()
                .post()
                .uri("/api/v1/currency-exchanges")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sourceAccountId", arsAccountId.toString(),
                        "targetAccountId", usdAccountId.toString(),
                        "sourceAmount", new BigDecimal("1185000"),
                        "targetAmount", new BigDecimal("1000"),
                        "exchangeDate", "2026-11-10"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(buyResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. Sell 200 USD for 250,000 ARS -> remaining 800 USD, PPP 1185, cost 948,000
        ResponseEntity<JsonNode> sellResp = client()
                .post()
                .uri("/api/v1/currency-exchanges")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sourceAccountId", usdAccountId.toString(),
                        "targetAccountId", arsAccountId.toString(),
                        "sourceAmount", new BigDecimal("200"),
                        "targetAmount", new BigDecimal("250000"),
                        "exchangeDate", "2026-11-12"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(sellResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID sellId = UUID.fromString(sellResp.getBody().path("id").asText());

        // Check positions after BUY + SELL
        ResponseEntity<JsonNode> posResp = client()
                .get()
                .uri("/api/v1/currency-exchanges/positions?view=PERSONAL")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(posResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode positions = posResp.getBody();
        assertThat(positions).isNotNull();
        assertThat(positions).hasSize(1);

        JsonNode usdPos = positions.get(0);
        assertThat(usdPos.path("currencyCode").asText()).isEqualTo("USD");
        assertThat(usdPos.path("units").asText()).isEqualTo("800.0000");
        assertThat(usdPos.path("weightedAvgRate").asText()).isEqualTo("1185.000000");
        assertThat(usdPos.path("totalCostBase").asText()).isEqualTo("948000.0000");
        assertThat(usdPos.path("currentRate").asText()).isEqualTo("1250.000000");
        assertThat(usdPos.path("currentValueBase").asText()).isEqualTo("1000000.0000");
        assertThat(usdPos.path("unrealizedResultBase").asText()).isEqualTo("52000.0000");
        assertThat(usdPos.path("unrealizedResultPct").asText()).isEqualTo("5.4852");

        // 3. Swap 100 USD -> 90 EUR (transfers 100 * 1185 = 118,500 ARS cost to EUR)
        ResponseEntity<JsonNode> swapResp = client()
                .post()
                .uri("/api/v1/currency-exchanges")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sourceAccountId", usdAccountId.toString(),
                        "targetAccountId", eurAccountId.toString(),
                        "sourceAmount", new BigDecimal("100"),
                        "targetAmount", new BigDecimal("90"),
                        "exchangeDate", "2026-11-15"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(swapResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(swapResp.getBody().path("operation").asText()).isEqualTo("SWAP");
        UUID swapId = UUID.fromString(swapResp.getBody().path("id").asText());

        ResponseEntity<JsonNode> afterSwapResp = client()
                .get()
                .uri("/api/v1/currency-exchanges/positions?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(afterSwapResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterSwapResp.getBody()).hasSize(2);
        JsonNode eurPos = afterSwapResp.getBody().get(0);
        JsonNode usdAfterSwap = afterSwapResp.getBody().get(1);
        assertThat(eurPos.path("currencyCode").asText()).isEqualTo("EUR");
        assertThat(eurPos.path("units").asText()).isEqualTo("90.0000");
        assertThat(eurPos.path("totalCostBase").asText()).isEqualTo("118500.0000");
        assertThat(eurPos.path("currentRate").asText()).isEqualTo("1400.000000");
        assertThat(eurPos.path("currentValueBase").asText()).isEqualTo("126000.0000");
        assertThat(eurPos.path("unrealizedResultBase").asText()).isEqualTo("7500.0000");

        assertThat(usdAfterSwap.path("currencyCode").asText()).isEqualTo("USD");
        assertThat(usdAfterSwap.path("units").asText()).isEqualTo("700.0000");
        assertThat(usdAfterSwap.path("totalCostBase").asText()).isEqualTo("829500.0000");

        // 4. Delete swap and sell -> only the initial 1000 USD buy remains in positions
        client().delete()
                .uri("/api/v1/currency-exchanges/" + swapId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toBodilessEntity();
        client().delete()
                .uri("/api/v1/currency-exchanges/" + sellId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toBodilessEntity();

        ResponseEntity<JsonNode> afterDeletePos = client()
                .get()
                .uri("/api/v1/currency-exchanges/positions?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(afterDeletePos.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterDeletePos.getBody()).hasSize(1);
        assertThat(afterDeletePos.getBody().get(0).path("currencyCode").asText()).isEqualTo("USD");
        assertThat(afterDeletePos.getBody().get(0).path("units").asText()).isEqualTo("1000.0000");
        assertThat(afterDeletePos.getBody().get(0).path("totalCostBase").asText())
                .isEqualTo("1185000.0000");

        // 5. HIJO OWN_ONLY with view=FAMILY -> 403
        ResponseEntity<JsonNode> childFamilyPositions = client()
                .get()
                .uri("/api/v1/currency-exchanges/positions?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childFamilyPositions.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void insertIndex(String indexCode, LocalDate effectiveDate, BigDecimal value) {
        jdbcTemplate.update(
                """
                INSERT INTO economic_indices (id, index_code, effective_date, value, source)
                VALUES (?, ?, ?, ?, 'MANUAL')
                """,
                UUID.randomUUID(),
                indexCode,
                effectiveDate,
                value);
    }
}
