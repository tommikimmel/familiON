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

    @Test
    void listViewsRespectAttributionAndVisibilityPolicy() {
        UUID familyId = fixtures.createFamily("Familia Views " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-v-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childOwnOnlyId = fixtures.createUser(
                familyId,
                "child-own-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        UUID childSummaryId = fixtures.createUser(
                familyId,
                "child-sum-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.FAMILY_SUMMARY);
        UUID childFullId = fixtures.createUser(
                familyId,
                "child-full-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.FAMILY_FULL);

        UUID adultAccount = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childOwnBearer =
                tokens.bearer(childOwnOnlyId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        String childSummaryBearer =
                tokens.bearer(childSummaryId, familyId, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        String childFullBearer =
                tokens.bearer(childFullId, familyId, Role.HIJO, FamilyVisibility.FAMILY_FULL);

        // Adult's own expense
        UUID adultExpenseId = UUID.fromString(postRaw(
                        adultBearer,
                        Map.of(
                                "type", "EXPENSE",
                                "amount", new BigDecimal("5000"),
                                "currencyCode", "ARS",
                                "transactionDate", "2026-11-10",
                                "accountId", adultAccount.toString(),
                                "description", "Adult coffee"))
                .getBody()
                .path("id")
                .asText());

        // Adult attributes expense to childOwnOnlyId
        UUID attributedExpenseId = UUID.fromString(postRaw(
                        adultBearer,
                        Map.of(
                                "type", "EXPENSE",
                                "amount", new BigDecimal("7500"),
                                "currencyCode", "ARS",
                                "transactionDate", "2026-11-11",
                                "accountId", adultAccount.toString(),
                                "description", "Child sneakers",
                                "attributedToUserId", childOwnOnlyId.toString()))
                .getBody()
                .path("id")
                .asText());

        // Child view=PERSONAL sees the attributed expense, NOT adult's own expense
        ResponseEntity<JsonNode> childPersonal =
                getRaw(childOwnBearer, "/api/v1/transactions?view=PERSONAL");
        assertThat(childPersonal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentIds(childPersonal.getBody()))
                .containsExactly(attributedExpenseId.toString());

        // Adult view=PERSONAL sees ONLY adult's own expense, NOT the one attributed to child
        ResponseEntity<JsonNode> adultPersonal =
                getRaw(adultBearer, "/api/v1/transactions?view=PERSONAL");
        assertThat(adultPersonal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentIds(adultPersonal.getBody()))
                .containsExactly(adultExpenseId.toString());

        // Adult view=FAMILY sees both
        ResponseEntity<JsonNode> adultFamily =
                getRaw(adultBearer, "/api/v1/transactions?view=FAMILY");
        assertThat(adultFamily.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentIds(adultFamily.getBody()))
                .containsExactly(attributedExpenseId.toString(), adultExpenseId.toString());

        // HIJO OWN_ONLY and FAMILY_SUMMARY listing view=FAMILY -> 403
        assertThat(getRaw(childOwnBearer, "/api/v1/transactions?view=FAMILY").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getRaw(childSummaryBearer, "/api/v1/transactions?view=FAMILY").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // HIJO FAMILY_FULL listing view=FAMILY -> 200 with both
        ResponseEntity<JsonNode> childFullFamily =
                getRaw(childFullBearer, "/api/v1/transactions?view=FAMILY");
        assertThat(childFullFamily.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentIds(childFullFamily.getBody())).hasSize(2);
    }

    @Test
    void listSupportsFiltersPaginationSizeClampingSortingAndAllFourLenses() {
        insertIndex("USD_MEP", LocalDate.of(2026, 10, 1), new BigDecimal("1000.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 10, 1), new BigDecimal("200.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 11, 1), new BigDecimal("250.000000"));

        UUID familyId = fixtures.createFamily("Familia Lenses " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-l-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID bankArs = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID bankUsd = fixtures.createAccount(familyId, adultId, "BANK", "USD");
        UUID foodCat = insertCategory(familyId, "Alimentos", "EXPENSE");
        String bearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        // Oct income: 100,000 ARS
        postRaw(
                bearer,
                Map.of(
                        "type", "INCOME",
                        "amount", new BigDecimal("100000"),
                        "currencyCode", "ARS",
                        "transactionDate", "2026-10-05",
                        "accountId", bankArs.toString(),
                        "description", "October Salary"));

        // Oct expense: 20,000 ARS (20% of Oct income, 20 USD ref, 25,000 constant base)
        UUID octExpenseId = UUID.fromString(postRaw(
                        bearer,
                        Map.of(
                                "type", "EXPENSE",
                                "amount", new BigDecimal("20000"),
                                "currencyCode", "ARS",
                                "transactionDate", "2026-10-15",
                                "accountId", bankArs.toString(),
                                "categoryId", foodCat.toString(),
                                "description", "Supermarket October"))
                .getBody()
                .path("id")
                .asText());

        // Nov expense in USD (no income in Nov -> INCOME_PCT is null)
        UUID novExpenseId = UUID.fromString(postRaw(
                        bearer,
                        Map.of(
                                "type", "EXPENSE",
                                "amount", new BigDecimal("50"),
                                "currencyCode", "USD",
                                "transactionDate", "2026-11-12",
                                "accountId", bankUsd.toString(),
                                "description", "Cloud Hosting"))
                .getBody()
                .path("id")
                .asText());

        // Installment plan row linked to verify installment {planId, number, total} join
        UUID cardArs = fixtures.createAccount(familyId, adultId, "CREDIT_CARD", "ARS");
        UUID planId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO installment_plans (
                    id, family_id, user_id, credit_card_account_id, category_id, description,
                    currency_code, total_installments, has_interest, financed_total,
                    installment_amount, purchase_date, first_billing_date, status
                )
                VALUES (?, ?, ?, ?, ?, 'TV 6 cuotas', 'ARS', 6, FALSE, 60000, 10000, '2026-09-01', '2026-09-10', 'ACTIVE')
                """,
                planId,
                familyId,
                adultId,
                cardArs,
                foodCat);
        UUID installmentTxId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id, family_id, user_id, attributed_to_user_id, account_id, category_id,
                    type, scope, status, amount, currency_code, transaction_date, description,
                    installment_plan_id, installment_number
                )
                VALUES (?, ?, ?, ?, ?, ?, 'EXPENSE', 'PERSONAL', 'PROJECTED', 10000, 'ARS', '2026-09-10', 'TV cuota 1', ?, 1)
                """,
                installmentTxId,
                familyId,
                adultId,
                adultId,
                cardArs,
                foodCat,
                planId);
        // Snapshot with NULL ipc_index_value so CONSTANT_BASE triggers lensApproximated = true
        jdbcTemplate.update(
                """
                INSERT INTO economic_snapshots (
                    transaction_id, reference_currency, reference_rate_source,
                    reference_exchange_rate, amount_in_reference, base_currency_amount,
                    ipc_index_value, uva_value, snapshot_date
                )
                VALUES (?, 'USD', 'USD_MEP', 1000.000000, 10.0000, 10000.0000, NULL, NULL, '2026-09-10')
                """,
                installmentTxId);

        // Filters: q, categoryId, currency, from/to, type
        ResponseEntity<JsonNode> filtered = getRaw(
                bearer,
                "/api/v1/transactions?view=PERSONAL&from=2026-10-01&to=2026-10-31&type=EXPENSE&categoryId="
                        + foodCat
                        + "&currency=ars&q=supermarket");
        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentIds(filtered.getBody())).containsExactly(octExpenseId.toString());

        // Pagination size=51 clamped to 50
        ResponseEntity<JsonNode> clamped =
                getRaw(bearer, "/api/v1/transactions?view=PERSONAL&page=0&size=51");
        assertThat(clamped.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clamped.getBody().path("page").path("size").asInt()).isEqualTo(50);
        assertThat(clamped.getBody().path("page").path("totalElements").asInt()).isEqualTo(4);

        // Sort by amount asc
        ResponseEntity<JsonNode> sorted =
                getRaw(bearer, "/api/v1/transactions?view=PERSONAL&sort=amount,asc");
        assertThat(sorted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sorted.getBody().path("content").get(0).path("id").asText())
                .isEqualTo(novExpenseId.toString());

        // CONSTANT_BASE lens: Oct expense = 20000 * 250 / 200 = 25000 (not approximated);
        // Installment tx has null snapshot IPC -> 10000 and lensApproximated = true
        ResponseEntity<JsonNode> constantResp =
                getRaw(bearer, "/api/v1/transactions?view=PERSONAL&lens=CONSTANT_BASE");
        assertThat(constantResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode octConstant = findItemById(constantResp.getBody(), octExpenseId);
        assertThat(octConstant.path("lens").asText()).isEqualTo("CONSTANT_BASE");
        assertThat(octConstant.path("lensAmount").asText()).isEqualTo("25000.0000");
        assertThat(octConstant.path("lensUnit").asText()).isEqualTo("ARS");
        assertThat(octConstant.path("lensApproximated").asBoolean()).isFalse();

        JsonNode instConstant = findItemById(constantResp.getBody(), installmentTxId);
        assertThat(instConstant.path("lensAmount").asText()).isEqualTo("10000.0000");
        assertThat(instConstant.path("lensApproximated").asBoolean()).isTrue();
        assertThat(instConstant.path("installment").path("planId").asText())
                .isEqualTo(planId.toString());
        assertThat(instConstant.path("installment").path("number").asInt()).isEqualTo(1);
        assertThat(instConstant.path("installment").path("total").asInt()).isEqualTo(6);

        // REFERENCE_CURRENCY lens
        ResponseEntity<JsonNode> refResp =
                getRaw(bearer, "/api/v1/transactions?view=PERSONAL&lens=REFERENCE_CURRENCY");
        JsonNode octRef = findItemById(refResp.getBody(), octExpenseId);
        assertThat(octRef.path("lensAmount").asText()).isEqualTo("20.0000");
        assertThat(octRef.path("lensUnit").asText()).isEqualTo("USD");

        // INCOME_PCT lens: Oct expense = 20.0000%, Nov expense (no Nov income) = null
        ResponseEntity<JsonNode> pctResp =
                getRaw(bearer, "/api/v1/transactions?view=PERSONAL&lens=INCOME_PCT");
        JsonNode octPct = findItemById(pctResp.getBody(), octExpenseId);
        assertThat(octPct.path("lensAmount").asText()).isEqualTo("20.0000");
        assertThat(octPct.path("lensUnit").asText()).isEqualTo("%");

        JsonNode novPct = findItemById(pctResp.getBody(), novExpenseId);
        assertThat(novPct.path("lensAmount").isNull()).isTrue();
        assertThat(novPct.path("lensUnit").asText()).isEqualTo("%");
    }

    @Test
    void editRecalculatesSnapshotPublishesEventEnforcesGuardsAndUpdatesBothTransferLegs() {
        insertIndex("USD_MEP", LocalDate.of(2026, 10, 1), new BigDecimal("1000.000000"));
        insertIndex("USD_MEP", LocalDate.of(2026, 11, 1), new BigDecimal("1250.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 10, 1), new BigDecimal("200.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 11, 1), new BigDecimal("250.000000"));

        UUID familyId = fixtures.createFamily("Familia Edit " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-ed-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childId = fixtures.createUser(
                familyId,
                "child-ed-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        UUID bankArs1 = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID bankArs2 = fixtures.createAccount(familyId, adultId, "WALLET", "ARS");
        UUID childWallet = fixtures.createAccount(familyId, childId, "WALLET", "ARS");
        UUID cat1 = insertCategory(familyId, "Servicios", "EXPENSE");
        UUID cat2 = insertCategory(familyId, "Hogar", "EXPENSE");

        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childBearer =
                tokens.bearer(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        // 1. Standard expense: edit amount & date -> snapshot recalculated + TransactionChangedEvent
        UUID txId = UUID.fromString(postRaw(
                        adultBearer,
                        Map.of(
                                "type", "EXPENSE",
                                "amount", new BigDecimal("10000"),
                                "currencyCode", "ARS",
                                "transactionDate", "2026-10-10",
                                "accountId", bankArs1.toString(),
                                "categoryId", cat1.toString(),
                                "description", "Internet bill"))
                .getBody()
                .path("id")
                .asText());

        capturedEvents.clear();
        ResponseEntity<JsonNode> patched = patchRaw(
                adultBearer,
                "/api/v1/transactions/" + txId,
                Map.of(
                        "amount", new BigDecimal("25000"),
                        "transactionDate", "2026-11-15",
                        "description", "Internet + Cable"));
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().path("amount").asText()).isEqualTo("25000.0000");
        assertThat(patched.getBody().path("transactionDate").asText()).isEqualTo("2026-11-15");
        // Omitting categoryId preserves the existing category
        assertThat(patched.getBody().path("category").path("id").asText())
                .isEqualTo(cat1.toString());

        // Explicitly passing categoryId: null clears the category while description is present
        java.util.Map<String, Object> clearCategoryBody = new java.util.HashMap<>();
        clearCategoryBody.put("categoryId", null);
        ResponseEntity<JsonNode> clearedCat =
                patchRaw(adultBearer, "/api/v1/transactions/" + txId, clearCategoryBody);
        assertThat(clearedCat.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clearedCat.getBody().path("category").isNull()).isTrue();

        Map<String, Object> snap = jdbcTemplate.queryForMap(
                "SELECT * FROM economic_snapshots WHERE transaction_id = ?", txId);
        assertThat((BigDecimal) snap.get("base_currency_amount"))
                .isEqualByComparingTo("25000.0000");
        assertThat((BigDecimal) snap.get("amount_in_reference")).isEqualByComparingTo("20.0000");
        assertThat((BigDecimal) snap.get("ipc_index_value")).isEqualByComparingTo("250.000000");
        assertThat(capturedEvents.changed)
                .anyMatch(e -> e.transactionId().equals(txId)
                        && !e.deleted()
                        && e.amount().compareTo(new BigDecimal("25000.0000")) == 0
                        && e.date().equals(LocalDate.of(2026, 11, 15)));
        assertThat(capturedEvents.financialChanged)
                .anyMatch(e -> e.familyId().equals(familyId));

        // 2. Non-author child cannot edit adult tx -> 403; child author cannot change attribution -> 403
        assertThat(patchRaw(childBearer, "/api/v1/transactions/" + txId, Map.of("description", "Hack"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        UUID childTxId = UUID.fromString(postRaw(
                        childBearer,
                        Map.of(
                                "type", "EXPENSE",
                                "amount", new BigDecimal("1000"),
                                "currencyCode", "ARS",
                                "accountId", childWallet.toString(),
                                "description", "Snacks"))
                .getBody()
                .path("id")
                .asText());
        assertThat(patchRaw(
                                childBearer,
                                "/api/v1/transactions/" + childTxId,
                                Map.of("attributedToUserId", adultId.toString()))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // 3. Transfer edit updates BOTH legs & recalculates BOTH snapshots; changing account -> 409
        UUID outId = UUID.fromString(postRaw(
                        adultBearer,
                        Map.of(
                                "type", "TRANSFER",
                                "amount", new BigDecimal("5000"),
                                "currencyCode", "ARS",
                                "transactionDate", "2026-10-12",
                                "accountId", bankArs1.toString(),
                                "targetAccountId", bankArs2.toString(),
                                "description", "Initial transfer"))
                .getBody()
                .path("id")
                .asText());
        UUID groupId = jdbcTemplate.queryForObject(
                "SELECT transfer_group_id FROM transactions WHERE id = ?", UUID.class, outId);

        assertThat(patchRaw(
                                adultBearer,
                                "/api/v1/transactions/" + outId,
                                Map.of("accountId", bankArs2.toString()))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        capturedEvents.clear();
        ResponseEntity<JsonNode> transferPatched = patchRaw(
                adultBearer,
                "/api/v1/transactions/" + outId,
                Map.of(
                        "amount", new BigDecimal("12500"),
                        "transactionDate", "2026-11-18",
                        "description", "Updated transfer",
                        "notes", "Adjusted"));
        assertThat(transferPatched.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> legs = jdbcTemplate.queryForList(
                """
                SELECT t.id, t.amount, t.transaction_date, t.description, t.notes, es.amount_in_reference
                FROM transactions t
                JOIN economic_snapshots es ON es.transaction_id = t.id
                WHERE t.transfer_group_id = ?
                """,
                groupId);
        assertThat(legs).hasSize(2);
        assertThat(legs)
                .allSatisfy(row -> {
                    assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("12500.0000");
                    assertThat(row.get("description")).isEqualTo("Updated transfer");
                    assertThat(row.get("notes")).isEqualTo("Adjusted");
                    assertThat((BigDecimal) row.get("amount_in_reference"))
                            .isEqualByComparingTo("10.0000");
                });
        assertThat(capturedEvents.changed).hasSize(2);

        // 4. Exchange transaction: description/notes OK, amount -> 409
        UUID exchangeTxId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id, family_id, user_id, attributed_to_user_id, account_id,
                    type, scope, status, amount, currency_code, transaction_date, description
                )
                VALUES (?, ?, ?, ?, ?, 'EXCHANGE_OUT', 'PERSONAL', 'POSTED', 1000, 'ARS', '2026-11-10', 'Buy USD')
                """,
                exchangeTxId,
                familyId,
                adultId,
                adultId,
                bankArs1);
        jdbcTemplate.update(
                """
                INSERT INTO economic_snapshots (
                    transaction_id, reference_currency, reference_rate_source,
                    reference_exchange_rate, amount_in_reference, base_currency_amount,
                    snapshot_date
                )
                VALUES (?, 'USD', 'USD_MEP', 1250.000000, 0.8000, 1000.0000, '2026-11-10')
                """,
                exchangeTxId);

        assertThat(patchRaw(
                                adultBearer,
                                "/api/v1/transactions/" + exchangeTxId,
                                Map.of("amount", new BigDecimal("2000")))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(patchRaw(
                                adultBearer,
                                "/api/v1/transactions/" + exchangeTxId,
                                Map.of("notes", "MEP operation"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // 5. Installment transaction: categoryId/description OK, amount -> 409, DELETE -> 409 "Use cancel-remaining"
        UUID cardArs = fixtures.createAccount(familyId, adultId, "CREDIT_CARD", "ARS");
        UUID planId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO installment_plans (
                    id, family_id, user_id, credit_card_account_id, category_id, description,
                    currency_code, total_installments, has_interest, financed_total,
                    installment_amount, purchase_date, first_billing_date, status
                )
                VALUES (?, ?, ?, ?, ?, 'Laptop 3 cuotas', 'ARS', 3, FALSE, 30000, 10000, '2026-11-01', '2026-11-10', 'ACTIVE')
                """,
                planId,
                familyId,
                adultId,
                cardArs,
                cat1);
        UUID instTxId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id, family_id, user_id, attributed_to_user_id, account_id, category_id,
                    type, scope, status, amount, currency_code, transaction_date, description,
                    installment_plan_id, installment_number
                )
                VALUES (?, ?, ?, ?, ?, ?, 'EXPENSE', 'PERSONAL', 'POSTED', 10000, 'ARS', '2026-11-10', 'Laptop 1/3', ?, 1)
                """,
                instTxId,
                familyId,
                adultId,
                adultId,
                cardArs,
                cat1,
                planId);
        jdbcTemplate.update(
                """
                INSERT INTO economic_snapshots (
                    transaction_id, reference_currency, reference_rate_source,
                    reference_exchange_rate, amount_in_reference, base_currency_amount,
                    snapshot_date
                )
                VALUES (?, 'USD', 'USD_MEP', 1250.000000, 8.0000, 10000.0000, '2026-11-10')
                """,
                instTxId);

        assertThat(patchRaw(
                                adultBearer,
                                "/api/v1/transactions/" + instTxId,
                                Map.of("amount", new BigDecimal("9000")))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(patchRaw(
                                adultBearer,
                                "/api/v1/transactions/" + instTxId,
                                Map.of("categoryId", cat2.toString(), "description", "Laptop work"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> delInst = deleteRaw(adultBearer, "/api/v1/transactions/" + instTxId);
        assertThat(delInst.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(delInst.getBody().path("detail").asText()).contains("Use cancel-remaining");

        // 6. Delete transfer soft-deletes BOTH legs & publishes TransactionChangedEvent(deleted=true)
        capturedEvents.clear();
        ResponseEntity<JsonNode> delTransfer =
                deleteRaw(adultBearer, "/api/v1/transactions/" + outId);
        assertThat(delTransfer.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer remainingLegs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE transfer_group_id = ? AND deleted_at IS NULL",
                Integer.class,
                groupId);
        assertThat(remainingLegs).isZero();
        assertThat(capturedEvents.changed)
                .hasSize(2)
                .allMatch(TransactionChangedEvent::deleted);

        // 7. Cross-family PATCH and DELETE -> 404
        UUID otherFamilyId = fixtures.createFamily("Other Edit " + UUID.randomUUID());
        UUID otherAdultId = fixtures.createUser(
                otherFamilyId,
                "other-ed-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        String otherBearer =
                tokens.bearer(otherAdultId, otherFamilyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        assertThat(patchRaw(otherBearer, "/api/v1/transactions/" + txId, Map.of("description", "X"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deleteRaw(otherBearer, "/api/v1/transactions/" + txId).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<JsonNode> patchRaw(
            String bearer, String uri, Map<String, Object> body) {
        return patchClient()
                .patch()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
    }

    private ResponseEntity<JsonNode> deleteRaw(String bearer, String uri) {
        return client()
                .delete()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
    }

    private ResponseEntity<JsonNode> getRaw(String bearer, String uri) {
        return client()
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
    }

    private List<String> contentIds(JsonNode pageBody) {
        List<String> ids = new java.util.ArrayList<>();
        pageBody.path("content").forEach(node -> ids.add(node.path("id").asText()));
        return ids;
    }

    private JsonNode findItemById(JsonNode pageBody, UUID id) {
        for (JsonNode node : pageBody.path("content")) {
            if (id.toString().equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("Transaction not found in page: " + id);
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
