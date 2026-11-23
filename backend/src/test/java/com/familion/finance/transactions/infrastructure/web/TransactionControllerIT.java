package com.familion.finance.transactions.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.events.FamilyFinancialDataChangedEvent;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.finance.transactions.application.api.ExpenseAttributedEvent;
import com.familion.finance.transactions.application.api.TransactionChangedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

@Import(TransactionControllerIT.EventCaptureConfig.class)
@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-"
        })
class TransactionControllerIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CapturedEvents capturedEvents;

    @BeforeEach
    void setUp() {
        capturedEvents.clear();
        jdbcTemplate.update("DELETE FROM economic_indices");
        Set<String> econKeys = redisTemplate.keys("econ:*");
        if (econKeys != null && !econKeys.isEmpty()) {
            redisTemplate.delete(econKeys);
        }
    }

    private RestClient patchClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void createExpenseSavesSnapshotWithEconomicsValuesAndReturnsTransactionItem() {
        insertIndex("USD_MEP", LocalDate.of(2026, 11, 1), new BigDecimal("1200.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 11, 1), new BigDecimal("250.000000"));
        insertIndex("UVA", LocalDate.of(2026, 11, 1), new BigDecimal("1300.000000"));

        UUID familyId = fixtures.createFamily("Familia Create " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-cr-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID accountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID categoryId = insertCategory(familyId, "Supermercado", "EXPENSE");
        String bearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "type", "EXPENSE",
                        "amount", new BigDecimal("24000"),
                        "currencyCode", "ARS",
                        "transactionDate", "2026-11-15",
                        "accountId", accountId.toString(),
                        "categoryId", categoryId.toString(),
                        "description", "Weekly groceries",
                        "notes", "Fresh fruit",
                        "scope", "FAMILY"))
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = resp.getBody();
        assertThat(body).isNotNull();
        UUID txId = UUID.fromString(body.path("id").asText());
        assertThat(body.path("type").asText()).isEqualTo("EXPENSE");
        assertThat(body.path("scope").asText()).isEqualTo("FAMILY");
        assertThat(body.path("status").asText()).isEqualTo("POSTED");
        assertThat(body.path("amount").asText()).isEqualTo("24000.0000");
        assertThat(body.path("currencyCode").asText()).isEqualTo("ARS");
        assertThat(body.path("transactionDate").asText()).isEqualTo("2026-11-15");
        assertThat(body.path("category").path("id").asText()).isEqualTo(categoryId.toString());
        assertThat(body.path("account").path("id").asText()).isEqualTo(accountId.toString());
        assertThat(body.path("paidBy").path("id").asText()).isEqualTo(adultId.toString());
        assertThat(body.path("attributedTo").path("id").asText()).isEqualTo(adultId.toString());
        assertThat(body.path("installment").isNull()).isTrue();
        assertThat(body.path("lens").asText()).isEqualTo("NOMINAL");
        assertThat(body.path("lensAmount").asText()).isEqualTo("24000.0000");
        assertThat(body.path("lensUnit").asText()).isEqualTo("ARS");
        assertThat(body.path("lensApproximated").asBoolean()).isFalse();

        Map<String, Object> snap = jdbcTemplate.queryForMap(
                "SELECT * FROM economic_snapshots WHERE transaction_id = ?", txId);
        assertThat((BigDecimal) snap.get("base_currency_amount"))
                .isEqualByComparingTo("24000.0000");
        assertThat((BigDecimal) snap.get("amount_in_reference")).isEqualByComparingTo("20.0000");
        assertThat((BigDecimal) snap.get("reference_exchange_rate"))
                .isEqualByComparingTo("1200.000000");
        assertThat((BigDecimal) snap.get("ipc_index_value")).isEqualByComparingTo("250.000000");
        assertThat((BigDecimal) snap.get("uva_value")).isEqualByComparingTo("1300.000000");

        assertThat(capturedEvents.financialChanged)
                .anyMatch(e -> e.familyId().equals(familyId));
    }

    @Test
    void createValidatesCurrencyByAccountTypeAndDescriptionWithoutCategory() {
        UUID familyId = fixtures.createFamily("Familia Val " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-val-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID bankArs = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID cardArs = fixtures.createAccount(familyId, adultId, "CREDIT_CARD", "ARS");
        String bearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        // Non-credit-card currency mismatch -> 422
        ResponseEntity<JsonNode> mismatch = postRaw(
                bearer,
                Map.of(
                        "type", "EXPENSE",
                        "amount", new BigDecimal("50"),
                        "currencyCode", "USD",
                        "accountId", bankArs.toString(),
                        "description", "USD on ARS bank"));
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Credit card accepts enabled currency USD -> 201
        ResponseEntity<JsonNode> cardUsd = postRaw(
                bearer,
                Map.of(
                        "type", "EXPENSE",
                        "amount", new BigDecimal("50"),
                        "currencyCode", "USD",
                        "accountId", cardArs.toString(),
                        "description", "Software subscription"));
        assertThat(cardUsd.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Credit card rejects disabled currency EUR -> 422
        ResponseEntity<JsonNode> cardEur = postRaw(
                bearer,
                Map.of(
                        "type", "EXPENSE",
                        "amount", new BigDecimal("50"),
                        "currencyCode", "EUR",
                        "accountId", cardArs.toString(),
                        "description", "EUR charge"));
        assertThat(cardEur.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // No category and no description -> 400
        ResponseEntity<JsonNode> noCatNoDesc = postRaw(
                bearer,
                Map.of(
                        "type", "EXPENSE",
                        "amount", new BigDecimal("100"),
                        "currencyCode", "ARS",
                        "accountId", bankArs.toString()));
        assertThat(noCatNoDesc.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createTransferCreatesMatchedPairAndRejectsCrossCurrencyWith422() {
        UUID familyId = fixtures.createFamily("Familia Trf " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-trf-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID bankArs1 = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID bankArs2 = fixtures.createAccount(familyId, adultId, "WALLET", "ARS");
        UUID bankUsd = fixtures.createAccount(familyId, adultId, "BANK", "USD");
        String bearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        ResponseEntity<JsonNode> diffCurrency = postRaw(
                bearer,
                Map.of(
                        "type", "TRANSFER",
                        "amount", new BigDecimal("1000"),
                        "currencyCode", "ARS",
                        "accountId", bankArs1.toString(),
                        "targetAccountId", bankUsd.toString()));
        assertThat(diffCurrency.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(diffCurrency.getBody().path("detail").asText())
                .contains("Use currency exchange");

        ResponseEntity<JsonNode> ok = postRaw(
                bearer,
                Map.of(
                        "type", "TRANSFER",
                        "amount", new BigDecimal("5000"),
                        "currencyCode", "ARS",
                        "transactionDate", "2026-11-18",
                        "accountId", bankArs1.toString(),
                        "targetAccountId", bankArs2.toString(),
                        "description", "Move to wallet"));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ok.getBody().path("type").asText()).isEqualTo("TRANSFER_OUT");
        assertThat(ok.getBody().path("category").isNull()).isTrue();

        UUID outId = UUID.fromString(ok.getBody().path("id").asText());
        UUID groupId = jdbcTemplate.queryForObject(
                "SELECT transfer_group_id FROM transactions WHERE id = ?", UUID.class, outId);
        assertThat(groupId).isNotNull();

        List<Map<String, Object>> legs = jdbcTemplate.queryForList(
                "SELECT type, account_id, amount FROM transactions WHERE transfer_group_id = ? ORDER BY type",
                groupId);
        assertThat(legs).hasSize(2);
        assertThat(legs.get(0).get("type")).isEqualTo("TRANSFER_IN");
        assertThat(legs.get(0).get("account_id")).isEqualTo(bankArs2);
        assertThat(legs.get(1).get("type")).isEqualTo("TRANSFER_OUT");
        assertThat(legs.get(1).get("account_id")).isEqualTo(bankArs1);

        // Transfers do not count as EXPENSE rows
        Integer expenseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE family_id = ? AND type = 'EXPENSE'",
                Integer.class,
                familyId);
        assertThat(expenseCount).isZero();
    }

    @Test
    void attributionRulesPublishEventRejectChildAttributionAndForceChildScopeToPersonal() {
        UUID familyId = fixtures.createFamily("Familia Attr " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-attr-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childId = fixtures.createUser(
                familyId,
                "child-attr-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        UUID adultAccount = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID childAccount = fixtures.createAccount(familyId, childId, "WALLET", "ARS");

        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childBearer =
                tokens.bearer(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        // Adult attributes expense to child -> 201 + ExpenseAttributedEvent
        ResponseEntity<JsonNode> attributed = postRaw(
                adultBearer,
                Map.of(
                        "type", "EXPENSE",
                        "amount", new BigDecimal("3500"),
                        "currencyCode", "ARS",
                        "accountId", adultAccount.toString(),
                        "description", "School lunch",
                        "attributedToUserId", childId.toString()));
        assertThat(attributed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID attributedId = UUID.fromString(attributed.getBody().path("id").asText());
        assertThat(capturedEvents.attributed)
                .anyMatch(e -> e.transactionId().equals(attributedId)
                        && e.paidByUserId().equals(adultId)
                        && e.attributedToUserId().equals(childId));

        // Child can fetch transaction attributed to them
        ResponseEntity<JsonNode> childGetOwn = client()
                .get()
                .uri("/api/v1/transactions/" + attributedId)
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(childGetOwn.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Child tries to attribute expense to adult -> 403
        ResponseEntity<JsonNode> childForbidden = postRaw(
                childBearer,
                Map.of(
                        "type", "EXPENSE",
                        "amount", new BigDecimal("500"),
                        "currencyCode", "ARS",
                        "accountId", childAccount.toString(),
                        "description", "Snack",
                        "attributedToUserId", adultId.toString()));
        assertThat(childForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Child requesting scope=FAMILY gets forced to PERSONAL
        ResponseEntity<JsonNode> childScope = postRaw(
                childBearer,
                Map.of(
                        "type", "EXPENSE",
                        "amount", new BigDecimal("500"),
                        "currencyCode", "ARS",
                        "accountId", childAccount.toString(),
                        "description", "Bus ticket",
                        "scope", "FAMILY"));
        assertThat(childScope.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(childScope.getBody().path("scope").asText()).isEqualTo("PERSONAL");

        // Cross-family isolation -> 404
        UUID otherFamilyId = fixtures.createFamily("Other " + UUID.randomUUID());
        UUID otherAdultId = fixtures.createUser(
                otherFamilyId,
                "other-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        String otherBearer =
                tokens.bearer(otherAdultId, otherFamilyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        ResponseEntity<JsonNode> crossFamily = client()
                .get()
                .uri("/api/v1/transactions/" + attributedId)
                .header(HttpHeaders.AUTHORIZATION, otherBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(crossFamily.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<JsonNode> postRaw(String bearer, Map<String, Object> body) {
        return client()
                .post()
                .uri("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
    }

    private void insertIndex(String code, LocalDate effectiveDate, BigDecimal value) {
        jdbcTemplate.update(
                """
                INSERT INTO economic_indices (id, index_code, effective_date, value, source)
                VALUES (?, ?, ?, ?, 'MANUAL')
                """,
                UUID.randomUUID(),
                code,
                effectiveDate,
                value);
    }

    private UUID insertCategory(UUID familyId, String name, String kind) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO categories (id, family_id, name, kind, icon, color_hex, is_system, is_active)
                VALUES (?, ?, ?, ?, 'shopping-cart', '#FF5733', FALSE, TRUE)
                """,
                id,
                familyId,
                name,
                kind);
        return id;
    }

    @TestConfiguration
    static class EventCaptureConfig {

        @Bean
        CapturedEvents capturedEvents() {
            return new CapturedEvents();
        }
    }

    static class CapturedEvents {
        final List<ExpenseAttributedEvent> attributed = new CopyOnWriteArrayList<>();
        final List<TransactionChangedEvent> changed = new CopyOnWriteArrayList<>();
        final List<FamilyFinancialDataChangedEvent> financialChanged =
                new CopyOnWriteArrayList<>();

        @EventListener
        void onAttributed(ExpenseAttributedEvent event) {
            attributed.add(event);
        }

        @EventListener
        void onChanged(TransactionChangedEvent event) {
            changed.add(event);
        }

        @EventListener
        void onFinancialChanged(FamilyFinancialDataChangedEvent event) {
            financialChanged.add(event);
        }

        void clear() {
            attributed.clear();
            changed.clear();
            financialChanged.clear();
        }
    }
}
