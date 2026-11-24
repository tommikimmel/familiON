package com.familion.family.allowances.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.family.allowances.application.api.AllowanceDisbursedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
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
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

@Import(AllowanceControllerIT.TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-",
            "spring.datasource.hikari.maximum-pool-size=3"
        })
class AllowanceControllerIT extends IntegrationTestBase {

    /** Fixed clock: Sunday 2026-11-15 12:00:00 ART (15:00:00Z). */
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        DisbursedEventRecorder disbursedEventRecorder() {
            return new DisbursedEventRecorder();
        }
    }

    static class DisbursedEventRecorder {
        private final List<AllowanceDisbursedEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void onDisbursed(AllowanceDisbursedEvent event) {
            events.add(event);
        }

        List<AllowanceDisbursedEvent> events() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DisbursedEventRecorder eventRecorder;

    private UUID familyId;
    private UUID adultId;
    private UUID otherAdultId;
    private UUID child1Id;
    private UUID child2Id;
    private UUID adultAccountId;
    private UUID sharedAccountId;
    private UUID child1AccountId;
    private UUID child2AccountId;
    private UUID expenseCategoryId;
    private String adultBearer;
    private String child1Bearer;
    private String child2Bearer;

    @BeforeEach
    void setUp() {
        eventRecorder.clear();
        jdbcTemplate.update("DELETE FROM economic_indices");
        Set<String> econKeys = redisTemplate.keys("econ:*");
        if (econKeys != null && !econKeys.isEmpty()) {
            redisTemplate.delete(econKeys);
        }
        Set<String> analyticsKeys = redisTemplate.keys("analytics:*");
        if (analyticsKeys != null && !analyticsKeys.isEmpty()) {
            redisTemplate.delete(analyticsKeys);
        }

        familyId = fixtures.createFamily("Familia Allowances " + UUID.randomUUID());
        adultId = fixtures.createUser(
                familyId,
                "adult-al-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        otherAdultId = fixtures.createUser(
                familyId,
                "adult2-al-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        child1Id = fixtures.createUser(
                familyId,
                "child1-al-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        child2Id = fixtures.createUser(
                familyId,
                "child2-al-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);

        jdbcTemplate.update(
                "UPDATE users SET accent_color = '#F59E0B' WHERE id = ?", child1Id);
        jdbcTemplate.update(
                "UPDATE users SET accent_color = '#10B981' WHERE id = ?", child2Id);

        adultAccountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        sharedAccountId = fixtures.createAccount(familyId, null, "BANK", "ARS");
        child1AccountId = fixtures.createAccount(familyId, child1Id, "WALLET", "ARS");
        child2AccountId = fixtures.createAccount(familyId, child2Id, "CASH", "ARS");
        expenseCategoryId = insertExpenseCategory(familyId, "Salidas " + UUID.randomUUID());

        adultBearer = tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        child1Bearer = tokens.bearer(child1Id, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        child2Bearer = tokens.bearer(child2Id, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
    }

    private RestClient patchClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void validationRejectsInvalidChildForeignTargetAccountAndChildMutations() {
        // Adult as childUserId -> 422
        ResponseEntity<JsonNode> adultAsChildResp = postExpectingError(
                "/api/v1/family/allowances",
                adultBearer,
                allowanceBody(
                        otherAdultId,
                        adultAccountId,
                        child1AccountId,
                        new BigDecimal("50000"),
                        "ARS",
                        "MONTHLY",
                        5,
                        "MANUAL"));
        assertThat(adultAsChildResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Non-existent childUserId -> 422
        ResponseEntity<JsonNode> unknownChildResp = postExpectingError(
                "/api/v1/family/allowances",
                adultBearer,
                allowanceBody(
                        UUID.randomUUID(),
                        adultAccountId,
                        child1AccountId,
                        new BigDecimal("50000"),
                        "ARS",
                        "MONTHLY",
                        5,
                        "MANUAL"));
        assertThat(unknownChildResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Child from another family -> 422
        UUID otherFamilyId = fixtures.createFamily("Other Family " + UUID.randomUUID());
        UUID otherFamilyChildId = fixtures.createUser(
                otherFamilyId,
                "other-child-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        UUID otherFamilyChildAccountId =
                fixtures.createAccount(otherFamilyId, otherFamilyChildId, "CASH", "ARS");

        ResponseEntity<JsonNode> otherFamilyChildResp = postExpectingError(
                "/api/v1/family/allowances",
                adultBearer,
                allowanceBody(
                        otherFamilyChildId,
                        adultAccountId,
                        otherFamilyChildAccountId,
                        new BigDecimal("50000"),
                        "ARS",
                        "MONTHLY",
                        5,
                        "MANUAL"));
        assertThat(otherFamilyChildResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Target account owned by adult -> 422
        ResponseEntity<JsonNode> adultAccountTargetResp = postExpectingError(
                "/api/v1/family/allowances",
                adultBearer,
                allowanceBody(
                        child1Id,
                        null,
                        adultAccountId,
                        new BigDecimal("50000"),
                        "ARS",
                        "MONTHLY",
                        5,
                        "MANUAL"));
        assertThat(adultAccountTargetResp.getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Target account shared (owner_user_id IS NULL) -> 422
        ResponseEntity<JsonNode> sharedTargetResp = postExpectingError(
                "/api/v1/family/allowances",
                adultBearer,
                allowanceBody(
                        child1Id,
                        null,
                        sharedAccountId,
                        new BigDecimal("50000"),
                        "ARS",
                        "MONTHLY",
                        5,
                        "MANUAL"));
        assertThat(sharedTargetResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Target account owned by another child -> 422
        ResponseEntity<JsonNode> otherChildTargetResp = postExpectingError(
                "/api/v1/family/allowances",
                adultBearer,
                allowanceBody(
                        child1Id,
                        adultAccountId,
                        child2AccountId,
                        new BigDecimal("50000"),
                        "ARS",
                        "MONTHLY",
                        5,
                        "MANUAL"));
        assertThat(otherChildTargetResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // WEEKLY with dayOfPeriod > 7 -> 422
        ResponseEntity<JsonNode> invalidWeeklyDayResp = postExpectingError(
                "/api/v1/family/allowances",
                adultBearer,
                allowanceBody(
                        child1Id,
                        adultAccountId,
                        child1AccountId,
                        new BigDecimal("10000"),
                        "ARS",
                        "WEEKLY",
                        8,
                        "MANUAL"));
        assertThat(invalidWeeklyDayResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // HIJO tries to create -> 403
        ResponseEntity<JsonNode> childCreateResp = postExpectingError(
                "/api/v1/family/allowances",
                child1Bearer,
                allowanceBody(
                        child1Id,
                        null,
                        child1AccountId,
                        new BigDecimal("50000"),
                        "ARS",
                        "MONTHLY",
                        5,
                        "MANUAL"));
        assertThat(childCreateResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Create a valid allowance as ADULTO, then verify HIJO cannot PATCH, DELETE, or DISBURSE
        UUID allowanceId = createAllowance(
                child1Id,
                adultAccountId,
                child1AccountId,
                new BigDecimal("50000"),
                "ARS",
                "MONTHLY",
                5,
                "MANUAL");

        ResponseEntity<JsonNode> childPatchResp = patchClient()
                .patch()
                .uri("/api/v1/family/allowances/" + allowanceId)
                .header(HttpHeaders.AUTHORIZATION, child1Bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", new BigDecimal("99999")))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childPatchResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> childDeleteResp = client()
                .delete()
                .uri("/api/v1/family/allowances/" + allowanceId)
                .header(HttpHeaders.AUTHORIZATION, child1Bearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childDeleteResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> childDisburseResp = postExpectingError(
                "/api/v1/family/allowances/" + allowanceId + "/disburse",
                child1Bearer,
                Map.of());
        assertThat(childDisburseResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void disburseGeneratesPairedTransactionsUpdatesBalanceAndPreventsHouseholdDoubleCounting() {
        UUID allowanceId = createAllowance(
                child1Id,
                adultAccountId,
                child1AccountId,
                new BigDecimal("50000"),
                "ARS",
                "MONTHLY",
                15,
                "MANUAL");

        // Disburse $50.000 on fixed today (2026-11-15)
        ResponseEntity<JsonNode> disburseResp = client()
                .post()
                .uri("/api/v1/family/allowances/" + allowanceId + "/disburse")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(disburseResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = disburseResp.getBody();
        assertThat(body).isNotNull();

        UUID outTxId = UUID.fromString(body.path("outTransactionId").asText());
        UUID inTxId = UUID.fromString(body.path("inTransactionId").asText());
        assertThat(outTxId).isNotEqualTo(inTxId);

        JsonNode allowanceNode = body.path("allowance");
        assertThat(allowanceNode.path("childBalance").asText()).isEqualTo("50000.0000");
        assertThat(allowanceNode.path("lastDisbursementDate").asText()).isEqualTo("2026-11-15");
        assertThat(allowanceNode.path("nextDisbursementDate").asText()).isEqualTo("2026-12-15");

        // Verify paired transactions in DB share transfer_group_id and allowance_id
        Map<String, Object> outRow = jdbcTemplate.queryForMap(
                """
                SELECT type, scope, status, amount, currency_code, transaction_date,
                       user_id, attributed_to_user_id, account_id, category_id,
                       allowance_id, transfer_group_id
                FROM transactions WHERE id = ?
                """,
                outTxId);
        Map<String, Object> inRow = jdbcTemplate.queryForMap(
                """
                SELECT type, scope, status, amount, currency_code, transaction_date,
                       user_id, attributed_to_user_id, account_id, category_id,
                       allowance_id, transfer_group_id
                FROM transactions WHERE id = ?
                """,
                inTxId);

        UUID expectedCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE system_key = 'ALLOWANCE_INCOME'", UUID.class);

        assertThat(outRow.get("type")).isEqualTo("ALLOWANCE_OUT");
        assertThat(outRow.get("scope")).isEqualTo("PERSONAL");
        assertThat(outRow.get("status")).isEqualTo("POSTED");
        assertThat((BigDecimal) outRow.get("amount")).isEqualByComparingTo("50000.0000");
        assertThat(outRow.get("user_id")).isEqualTo(adultId);
        assertThat(outRow.get("attributed_to_user_id")).isEqualTo(adultId);
        assertThat(outRow.get("account_id")).isEqualTo(adultAccountId);
        assertThat(outRow.get("category_id")).isNull();
        assertThat(outRow.get("allowance_id")).isEqualTo(allowanceId);
        assertThat(outRow.get("transfer_group_id")).isNotNull();

        assertThat(inRow.get("type")).isEqualTo("ALLOWANCE_IN");
        assertThat(inRow.get("scope")).isEqualTo("PERSONAL");
        assertThat(inRow.get("status")).isEqualTo("POSTED");
        assertThat((BigDecimal) inRow.get("amount")).isEqualByComparingTo("50000.0000");
        assertThat(inRow.get("user_id")).isEqualTo(adultId);
        assertThat(inRow.get("attributed_to_user_id")).isEqualTo(child1Id);
        assertThat(inRow.get("account_id")).isEqualTo(child1AccountId);
        assertThat(inRow.get("category_id")).isEqualTo(expectedCategoryId);
        assertThat(inRow.get("allowance_id")).isEqualTo(allowanceId);
        assertThat(inRow.get("transfer_group_id")).isEqualTo(outRow.get("transfer_group_id"));

        // Verify AllowanceDisbursedEvent was published
        assertThat(eventRecorder.events())
                .containsExactly(new AllowanceDisbursedEvent(
                        familyId, allowanceId, child1Id, new BigDecimal("50000.0000"), "ARS"));

        // Rule 1 check BEFORE child spends: FAMILY summary must show 0 expense and 0 income
        ResponseEntity<JsonNode> familySummaryBefore = client()
                .get()
                .uri("/api/v1/analytics/summary?view=FAMILY&date=2026-11-15")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(familySummaryBefore.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(familySummaryBefore.getBody().path("expense").asText()).isEqualTo("0.0000");
        assertThat(familySummaryBefore.getBody().path("income").asText()).isEqualTo("0.0000");

        // Child spends $30.000 from their allowance account
        ResponseEntity<JsonNode> childExpenseResp = client()
                .post()
                .uri("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, child1Bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "type", "EXPENSE",
                        "amount", new BigDecimal("30000"),
                        "currencyCode", "ARS",
                        "transactionDate", "2026-11-15",
                        "accountId", child1AccountId.toString(),
                        "categoryId", expenseCategoryId.toString(),
                        "description", "Cine con amigos"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(childExpenseResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Rule 1 check AFTER child spends $30.000:
        // FAMILY summary must show $30.000 expense (NOT $80.000!)
        ResponseEntity<JsonNode> familySummaryAfter = client()
                .get()
                .uri("/api/v1/analytics/summary?view=FAMILY&date=2026-11-15")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(familySummaryAfter.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(familySummaryAfter.getBody().path("expense").asText()).isEqualTo("30000.0000");
        assertThat(familySummaryAfter.getBody().path("income").asText()).isEqualTo("0.0000");

        // Child's PERSONAL summary counts ALLOWANCE_IN as income ($50.000) and expense ($30.000)
        ResponseEntity<JsonNode> childSummary = client()
                .get()
                .uri("/api/v1/analytics/summary?view=PERSONAL&date=2026-11-15")
                .header(HttpHeaders.AUTHORIZATION, child1Bearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(childSummary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(childSummary.getBody().path("income").asText()).isEqualTo("50000.0000");
        assertThat(childSummary.getBody().path("expense").asText()).isEqualTo("30000.0000");
        assertThat(childSummary.getBody().path("balance").asText()).isEqualTo("20000.0000");

        // Allowance listing reflects updated childBalance ($20.000)
        JsonNode childList = client()
                .get()
                .uri("/api/v1/family/allowances")
                .header(HttpHeaders.AUTHORIZATION, child1Bearer)
                .retrieve()
                .body(JsonNode.class);
        assertThat(childList).hasSize(1);
        assertThat(childList.get(0).path("childBalance").asText()).isEqualTo("20000.0000");
    }

    @Test
    void currentAmountSupportsIpcAndReferenceCurrencyAndSchedulesClampMonthEnd() {
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 1, 1), new BigDecimal("100.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 11, 1), new BigDecimal("140.000000"));
        insertIndex("USD_MEP", LocalDate.of(2026, 11, 15), new BigDecimal("1250.000000"));

        // Monthly IPC allowance with dayOfPeriod = 31 (clamps to Nov 30, 2026 before disburse)
        UUID ipcAllowanceId = createAllowance(
                child1Id,
                adultAccountId,
                child1AccountId,
                new BigDecimal("50000"),
                "ARS",
                "MONTHLY",
                31,
                "IPC");
        // Backdate created_at to 2026-01-15 so IPC(created_at) = 100 and IPC(latest) = 140
        jdbcTemplate.update(
                "UPDATE allowances SET created_at = TIMESTAMPTZ '2026-01-15T15:00:00Z' WHERE id = ?",
                ipcAllowanceId);

        // Weekly REFERENCE_CURRENCY allowance (40 USD * 1250 = 50000 ARS), Monday = 1
        UUID weeklyRefId = createAllowance(
                child2Id,
                null,
                child2AccountId,
                new BigDecimal("40"),
                "ARS",
                "WEEKLY",
                1,
                "REFERENCE_CURRENCY");

        Map<UUID, JsonNode> byId = indexById(client()
                .get()
                .uri("/api/v1/family/allowances")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .body(JsonNode.class));

        // IPC: 50000 * 140 / 100 = 70000.0000; day 31 in Nov -> 2026-11-30
        JsonNode ipcNode = byId.get(ipcAllowanceId);
        assertThat(ipcNode.path("currentAmount").asText()).isEqualTo("70000.0000");
        assertThat(ipcNode.path("nextDisbursementDate").asText()).isEqualTo("2026-11-30");
        assertThat(ipcNode.path("lastDisbursementDate").isNull()).isTrue();

        // REFERENCE_CURRENCY: 40 USD * 1250 = 50000.0000; today is Sun 2026-11-15 -> next Mon 2026-11-16
        JsonNode weeklyNode = byId.get(weeklyRefId);
        assertThat(weeklyNode.path("currentAmount").asText()).isEqualTo("50000.0000");
        assertThat(weeklyNode.path("nextDisbursementDate").asText()).isEqualTo("2026-11-16");

        // Disburse IPC allowance on 2026-01-31 (without explicit amount -> uses currentAmount 70000)
        // Next occurrence of day 31 after 2026-01-31 clamps to 2026-02-28!
        ResponseEntity<JsonNode> ipcDisburse = client()
                .post()
                .uri("/api/v1/family/allowances/" + ipcAllowanceId + "/disburse")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("date", "2026-01-31"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(ipcDisburse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ipcDisburse.getBody().path("allowance").path("lastDisbursementDate").asText())
                .isEqualTo("2026-01-31");
        assertThat(ipcDisburse.getBody().path("allowance").path("nextDisbursementDate").asText())
                .isEqualTo("2026-02-28");
        assertThat(ipcDisburse.getBody().path("allowance").path("childBalance").asText())
                .isEqualTo("70000.0000");

        // Disburse weekly allowance without sourceAccountId on Mon 2026-11-16 -> outTransactionId is null,
        // nextDisbursementDate advances to Mon 2026-11-23
        ResponseEntity<JsonNode> weeklyDisburse = client()
                .post()
                .uri("/api/v1/family/allowances/" + weeklyRefId + "/disburse")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("date", "2026-11-16"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(weeklyDisburse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(weeklyDisburse.getBody().path("outTransactionId").isNull()).isTrue();
        assertThat(weeklyDisburse.getBody().path("inTransactionId").asText()).isNotBlank();
        assertThat(weeklyDisburse.getBody().path("allowance").path("lastDisbursementDate").asText())
                .isEqualTo("2026-11-16");
        assertThat(weeklyDisburse.getBody().path("allowance").path("nextDisbursementDate").asText())
                .isEqualTo("2026-11-23");
    }

    @Test
    void childListsOnlyOwnAllowancesAndPatchAndSoftDeleteWork() {
        UUID child1AllowanceId = createAllowance(
                child1Id,
                adultAccountId,
                child1AccountId,
                new BigDecimal("40000"),
                "ARS",
                "MONTHLY",
                5,
                "MANUAL");
        UUID child2AllowanceId = createAllowance(
                child2Id,
                adultAccountId,
                child2AccountId,
                new BigDecimal("35000"),
                "ARS",
                "WEEKLY",
                5,
                "MANUAL");

        // Adult sees both
        Map<UUID, JsonNode> adultList = indexById(client()
                .get()
                .uri("/api/v1/family/allowances")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .body(JsonNode.class));
        assertThat(adultList).containsOnlyKeys(child1AllowanceId, child2AllowanceId);
        assertThat(adultList.get(child1AllowanceId).path("child").path("accentColor").asText())
                .isEqualTo("#F59E0B");
        assertThat(adultList.get(child1AllowanceId).path("assignedBy").path("id").asText())
                .isEqualTo(adultId.toString());
        assertThat(adultList.get(child1AllowanceId).path("isActive").asBoolean()).isTrue();

        // Child 1 sees ONLY own allowance
        Map<UUID, JsonNode> child1List = indexById(client()
                .get()
                .uri("/api/v1/family/allowances")
                .header(HttpHeaders.AUTHORIZATION, child1Bearer)
                .retrieve()
                .body(JsonNode.class));
        assertThat(child1List).containsOnlyKeys(child1AllowanceId);

        // Child 2 sees ONLY own allowance
        Map<UUID, JsonNode> child2List = indexById(client()
                .get()
                .uri("/api/v1/family/allowances")
                .header(HttpHeaders.AUTHORIZATION, child2Bearer)
                .retrieve()
                .body(JsonNode.class));
        assertThat(child2List).containsOnlyKeys(child2AllowanceId);

        // PATCH: update amount and explicitly clear sourceAccountId to null
        Map<String, Object> patchBody = new HashMap<>();
        patchBody.put("amount", new BigDecimal("45000"));
        patchBody.put("sourceAccountId", null);

        ResponseEntity<JsonNode> patched = patchClient()
                .patch()
                .uri("/api/v1/family/allowances/" + child1AllowanceId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(patchBody)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().path("amount").asText()).isEqualTo("45000.0000");
        assertThat(patched.getBody().path("sourceAccount").isNull()).isTrue();

        // DELETE soft-deactivates (is_active = false)
        ResponseEntity<Void> deleted = client()
                .delete()
                .uri("/api/v1/family/allowances/" + child2AllowanceId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toBodilessEntity();
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<UUID, JsonNode> activeOnly = indexById(client()
                .get()
                .uri("/api/v1/family/allowances")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .body(JsonNode.class));
        assertThat(activeOnly).containsOnlyKeys(child1AllowanceId);

        Map<UUID, JsonNode> withInactive = indexById(client()
                .get()
                .uri("/api/v1/family/allowances?includeInactive=true")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .body(JsonNode.class));
        assertThat(withInactive).containsOnlyKeys(child1AllowanceId, child2AllowanceId);
        assertThat(withInactive.get(child2AllowanceId).path("isActive").asBoolean()).isFalse();

        // Disbursing inactive allowance -> 422
        ResponseEntity<JsonNode> inactiveDisburse = postExpectingError(
                "/api/v1/family/allowances/" + child2AllowanceId + "/disburse",
                adultBearer,
                Map.of());
        assertThat(inactiveDisburse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private UUID createAllowance(
            UUID childUserId,
            UUID sourceAccountId,
            UUID targetAccountId,
            BigDecimal amount,
            String currencyCode,
            String frequency,
            int dayOfPeriod,
            String adjustmentRule) {
        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/family/allowances")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(allowanceBody(
                        childUserId,
                        sourceAccountId,
                        targetAccountId,
                        amount,
                        currencyCode,
                        frequency,
                        dayOfPeriod,
                        adjustmentRule))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        return UUID.fromString(resp.getBody().path("id").asText());
    }

    private static Map<String, Object> allowanceBody(
            UUID childUserId,
            UUID sourceAccountId,
            UUID targetAccountId,
            BigDecimal amount,
            String currencyCode,
            String frequency,
            int dayOfPeriod,
            String adjustmentRule) {
        Map<String, Object> body = new HashMap<>();
        body.put("childUserId", childUserId.toString());
        if (sourceAccountId != null) {
            body.put("sourceAccountId", sourceAccountId.toString());
        }
        body.put("targetAccountId", targetAccountId.toString());
        body.put("amount", amount);
        body.put("currencyCode", currencyCode);
        body.put("frequency", frequency);
        body.put("dayOfPeriod", dayOfPeriod);
        body.put("adjustmentRule", adjustmentRule);
        return body;
    }

    private ResponseEntity<JsonNode> postExpectingError(
            String uri, String bearer, Map<String, Object> body) {
        return client()
                .post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
    }

    private static Map<UUID, JsonNode> indexById(JsonNode arrayNode) {
        Map<UUID, JsonNode> map = new HashMap<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                map.put(UUID.fromString(node.path("id").asText()), node);
            }
        }
        return map;
    }

    private UUID insertExpenseCategory(UUID famId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO categories (id, family_id, name, kind, icon, color_hex, is_system, is_active)
                VALUES (?, ?, ?, 'EXPENSE', 'party-popper', '#2563EB', FALSE, TRUE)
                """,
                id,
                famId,
                name);
        return id;
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
