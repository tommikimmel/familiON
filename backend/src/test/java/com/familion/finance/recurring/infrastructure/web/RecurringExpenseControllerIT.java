package com.familion.finance.recurring.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.finance.recurring.application.api.RecurringDue;
import com.familion.finance.recurring.application.api.RecurringExpenseQueryApi;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

@Import(RecurringExpenseControllerIT.FixedClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-",
            "spring.datasource.hikari.maximum-pool-size=3"
        })
class RecurringExpenseControllerIT extends IntegrationTestBase {

    /** Fixed clock: 2026-11-15 12:00:00 ART (15:00:00Z). */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RecurringExpenseQueryApi recurringExpenseQueryApi;

    private UUID familyId;
    private UUID adultId;
    private UUID otherAdultId;
    private UUID childId;
    private UUID adultAccountId;
    private UUID childAccountId;
    private UUID categoryId;
    private String adultBearer;
    private String otherAdultBearer;
    private String childBearer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM economic_indices");
        Set<String> econKeys = redisTemplate.keys("econ:*");
        if (econKeys != null && !econKeys.isEmpty()) {
            redisTemplate.delete(econKeys);
        }

        familyId = fixtures.createFamily("Familia Recurring " + UUID.randomUUID());
        adultId = fixtures.createUser(
                familyId,
                "adult-rec-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        otherAdultId = fixtures.createUser(
                familyId,
                "adult2-rec-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        childId = fixtures.createUser(
                familyId,
                "child-rec-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);

        adultAccountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        childAccountId = fixtures.createAccount(familyId, childId, "CASH", "ARS");
        categoryId = insertExpenseCategory(familyId, "Servicios " + UUID.randomUUID());

        adultBearer = tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        otherAdultBearer =
                tokens.bearer(otherAdultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childBearer = tokens.bearer(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
    }

    private RestClient patchClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void childCannotCreatePatchOrDeleteRecurringExpense() {
        ResponseEntity<JsonNode> createResp = postExpectingError(
                "/api/v1/recurring-expenses",
                childBearer,
                Map.of(
                        "name", "Netflix",
                        "categoryId", categoryId.toString(),
                        "baseAmount", new BigDecimal("15000"),
                        "currencyCode", "ARS",
                        "dueDay", 10,
                        "adjustmentRule", "MANUAL",
                        "startDate", "2026-01-01"));
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        UUID createdId = createRecurring(
                adultBearer,
                "Internet",
                adultAccountId,
                "FAMILY",
                new BigDecimal("45000"),
                "ARS",
                12,
                "MANUAL",
                "2026-01-01",
                null);

        ResponseEntity<JsonNode> patchResp = patchClient()
                .patch()
                .uri("/api/v1/recurring-expenses/" + createdId)
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Hacked"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> deleteResp = client()
                .delete()
                .uri("/api/v1/recurring-expenses/" + createdId)
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listMonthResolvesPendingOverduePaidAndNotApplicableWithFixedClock() {
        // Fixed clock today = 2026-11-15
        UUID overdueId = createRecurring(
                adultBearer,
                "Alquiler",
                adultAccountId,
                "FAMILY",
                new BigDecimal("400000"),
                "ARS",
                5,
                "MANUAL",
                "2026-01-01",
                null);
        UUID pendingId = createRecurring(
                adultBearer,
                "Prepaga",
                adultAccountId,
                "FAMILY",
                new BigDecimal("120000"),
                "ARS",
                20,
                "MANUAL",
                "2026-01-01",
                null);
        UUID paidId = createRecurring(
                adultBearer,
                "Expensas",
                adultAccountId,
                "FAMILY",
                new BigDecimal("90000"),
                "ARS",
                10,
                "MANUAL",
                "2026-01-01",
                null);
        UUID futureId = createRecurring(
                adultBearer,
                "Seguro Verano",
                adultAccountId,
                "FAMILY",
                new BigDecimal("50000"),
                "ARS",
                15,
                "MANUAL",
                "2026-12-01",
                null);

        // Confirm payment for Expensas in 2026-11
        ResponseEntity<JsonNode> confirmResp = client()
                .post()
                .uri("/api/v1/recurring-expenses/" + paidId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "month", "2026-11",
                        "amount", new BigDecimal("95000.50"),
                        "paymentDate", "2026-11-08"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(confirmResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode confirmedBody = confirmResp.getBody();
        assertThat(confirmedBody).isNotNull();
        assertThat(confirmedBody.path("month").path("status").asText()).isEqualTo("PAID");
        assertThat(confirmedBody.path("month").path("paidAmount").asText()).isEqualTo("95000.5000");
        assertThat(confirmedBody.path("month").path("paidDate").asText()).isEqualTo("2026-11-08");
        UUID linkedTxId = UUID.fromString(
                confirmedBody.path("month").path("paidTransactionId").asText());

        // Verify linked transaction in DB
        Map<String, Object> txRow = jdbcTemplate.queryForMap(
                """
                SELECT type, scope, status, amount, currency_code, transaction_date, description,
                       recurring_expense_id, user_id, attributed_to_user_id, account_id, category_id
                FROM transactions
                WHERE id = ?
                """,
                linkedTxId);
        assertThat(txRow.get("type")).isEqualTo("EXPENSE");
        assertThat(txRow.get("scope")).isEqualTo("FAMILY");
        assertThat(txRow.get("status")).isEqualTo("POSTED");
        assertThat((BigDecimal) txRow.get("amount")).isEqualByComparingTo("95000.5000");
        assertThat(txRow.get("currency_code")).isEqualTo("ARS");
        assertThat(txRow.get("transaction_date").toString()).isEqualTo("2026-11-08");
        assertThat(txRow.get("description")).isEqualTo("Expensas");
        assertThat(txRow.get("recurring_expense_id")).isEqualTo(paidId);
        assertThat(txRow.get("user_id")).isEqualTo(adultId);
        assertThat(txRow.get("attributed_to_user_id")).isEqualTo(adultId);
        assertThat(txRow.get("account_id")).isEqualTo(adultAccountId);
        assertThat(txRow.get("category_id")).isEqualTo(categoryId);

        // List 2026-11
        ResponseEntity<JsonNode> listResp = client()
                .get()
                .uri("/api/v1/recurring-expenses?month=2026-11")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<UUID, JsonNode> byId = indexById(listResp.getBody());

        assertThat(byId.get(overdueId).path("month").path("status").asText()).isEqualTo("OVERDUE");
        assertThat(byId.get(overdueId).path("month").path("dueDate").asText())
                .isEqualTo("2026-11-05");
        assertThat(byId.get(pendingId).path("month").path("status").asText()).isEqualTo("PENDING");
        assertThat(byId.get(pendingId).path("month").path("dueDate").asText())
                .isEqualTo("2026-11-20");
        assertThat(byId.get(paidId).path("month").path("status").asText()).isEqualTo("PAID");
        assertThat(byId.get(paidId).path("month").path("paidTransactionId").asText())
                .isEqualTo(linkedTxId.toString());
        assertThat(byId.get(futureId).path("month").path("status").asText())
                .isEqualTo("NOT_APPLICABLE");
        assertThat(byId.get(overdueId).path("isActive").asBoolean()).isTrue();
    }

    @Test
    void doubleConfirmationReturns409AndNotApplicableOrMissingAccountReturns422() {
        UUID recurringId = createRecurring(
                adultBearer,
                "Colegio",
                null,
                "FAMILY",
                new BigDecimal("180000"),
                "ARS",
                10,
                "MANUAL",
                "2026-11-01",
                "2026-12-31");

        // Missing account (neither suggested nor in request) -> 422
        ResponseEntity<JsonNode> noAccountResp = postExpectingError(
                "/api/v1/recurring-expenses/" + recurringId + "/confirm-payment",
                adultBearer,
                Map.of("month", "2026-11", "amount", new BigDecimal("180000")));
        assertThat(noAccountResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // NOT_APPLICABLE month -> 422
        ResponseEntity<JsonNode> notApplicableResp = postExpectingError(
                "/api/v1/recurring-expenses/" + recurringId + "/confirm-payment",
                adultBearer,
                Map.of(
                        "month", "2026-10",
                        "amount", new BigDecimal("180000"),
                        "accountId", adultAccountId.toString()));
        assertThat(notApplicableResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // First confirmation with explicit account -> 201
        ResponseEntity<JsonNode> firstConfirm = client()
                .post()
                .uri("/api/v1/recurring-expenses/" + recurringId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "month", "2026-11",
                        "amount", new BigDecimal("185000"),
                        "accountId", adultAccountId.toString()))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(firstConfirm.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Double confirmation -> 409
        ResponseEntity<JsonNode> secondConfirm = postExpectingError(
                "/api/v1/recurring-expenses/" + recurringId + "/confirm-payment",
                adultBearer,
                Map.of(
                        "month", "2026-11",
                        "amount", new BigDecimal("185000"),
                        "accountId", adultAccountId.toString()));
        assertThat(secondConfirm.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void adjustmentRulesEstimateAmountsFromEconomicIndicesAndPreviousPayments() {
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 1, 1), new BigDecimal("100.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 11, 1), new BigDecimal("140.000000"));
        insertIndex("IPC_MENSUAL", LocalDate.of(2026, 10, 1), new BigDecimal("4.000000"));
        insertIndex("USD_MEP", LocalDate.of(2026, 11, 15), new BigDecimal("1250.000000"));

        UUID ipcId = createRecurring(
                adultBearer,
                "Alquiler IPC",
                adultAccountId,
                "FAMILY",
                new BigDecimal("300000"),
                "ARS",
                10,
                "IPC",
                "2026-01-01",
                null);
        UUID refCurrencyId = createRecurring(
                adultBearer,
                "Cloud USD",
                adultAccountId,
                "FAMILY",
                new BigDecimal("50"),
                "ARS",
                18,
                "REFERENCE_CURRENCY",
                "2026-01-01",
                null);
        UUID lastPaidId = createRecurring(
                adultBearer,
                "Luz",
                adultAccountId,
                "FAMILY",
                new BigDecimal("40000"),
                "ARS",
                22,
                "LAST_PAID",
                "2026-01-01",
                null);

        // Pay October for LAST_PAID expense -> 50000
        ResponseEntity<JsonNode> octPay = client()
                .post()
                .uri("/api/v1/recurring-expenses/" + lastPaidId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "month", "2026-10",
                        "amount", new BigDecimal("50000"),
                        "paymentDate", "2026-10-20"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(octPay.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> listResp = client()
                .get()
                .uri("/api/v1/recurring-expenses?month=2026-11")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<UUID, JsonNode> byId = indexById(listResp.getBody());

        // IPC: 300000 * 140 / 100 = 420000.0000
        assertThat(byId.get(ipcId).path("month").path("estimatedAmount").asText())
                .isEqualTo("420000.0000");
        // REFERENCE_CURRENCY: 50 USD * 1250 = 62500.0000 ARS
        assertThat(byId.get(refCurrencyId).path("month").path("estimatedAmount").asText())
                .isEqualTo("62500.0000");
        // LAST_PAID: 50000 * (1 + 4/100) = 52000.0000
        assertThat(byId.get(lastPaidId).path("month").path("estimatedAmount").asText())
                .isEqualTo("52000.0000");
    }

    @Test
    void visibilityPatchAndSoftDeactivateWorkCorrectly() {
        UUID familyExpenseId = createRecurring(
                adultBearer,
                "Gas",
                adultAccountId,
                "FAMILY",
                new BigDecimal("35000"),
                "ARS",
                15,
                "MANUAL",
                "2026-01-01",
                "2026-12-31");
        UUID adultPersonalId = createRecurring(
                adultBearer,
                "Gimnasio Adulto",
                adultAccountId,
                "PERSONAL",
                new BigDecimal("30000"),
                "ARS",
                15,
                "MANUAL",
                "2026-01-01",
                null);

        // Other adult sees FAMILY but not adult1's PERSONAL
        ResponseEntity<JsonNode> otherAdultList = client()
                .get()
                .uri("/api/v1/recurring-expenses?month=2026-11")
                .header(HttpHeaders.AUTHORIZATION, otherAdultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        Map<UUID, JsonNode> otherAdultMap = indexById(otherAdultList.getBody());
        assertThat(otherAdultMap).containsKey(familyExpenseId);
        assertThat(otherAdultMap).doesNotContainKey(adultPersonalId);

        // Child sees neither FAMILY nor adult's PERSONAL
        ResponseEntity<JsonNode> childList = client()
                .get()
                .uri("/api/v1/recurring-expenses?month=2026-11")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(childList.getBody()).isEmpty();

        // Child cannot confirm another member's PERSONAL expense -> 403
        ResponseEntity<JsonNode> childPersonalConfirm = postExpectingError(
                "/api/v1/recurring-expenses/" + adultPersonalId + "/confirm-payment",
                childBearer,
                Map.of(
                        "month", "2026-11",
                        "amount", new BigDecimal("30000"),
                        "accountId", childAccountId.toString()));
        assertThat(childPersonalConfirm.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Child CAN confirm FAMILY expense using own account
        ResponseEntity<JsonNode> childConfirm = client()
                .post()
                .uri("/api/v1/recurring-expenses/" + familyExpenseId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "month", "2026-11",
                        "amount", new BigDecimal("36000"),
                        "accountId", childAccountId.toString()))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(childConfirm.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // PATCH: clear accountId and endDate, update name and baseAmount
        Map<String, Object> patchBody = new HashMap<>();
        patchBody.put("name", "Gas Natural");
        patchBody.put("baseAmount", new BigDecimal("40000"));
        patchBody.put("accountId", null);
        patchBody.put("endDate", null);

        ResponseEntity<JsonNode> patched = patchClient()
                .patch()
                .uri("/api/v1/recurring-expenses/" + familyExpenseId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(patchBody)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().path("name").asText()).isEqualTo("Gas Natural");
        assertThat(patched.getBody().path("baseAmount").asText()).isEqualTo("40000.0000");
        assertThat(patched.getBody().path("account").isNull()).isTrue();
        assertThat(patched.getBody().path("endDate").isNull()).isTrue();

        // DELETE soft-deactivates
        ResponseEntity<Void> deleted = client()
                .delete()
                .uri("/api/v1/recurring-expenses/" + adultPersonalId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toBodilessEntity();
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<UUID, JsonNode> activeOnly = indexById(client()
                .get()
                .uri("/api/v1/recurring-expenses?month=2026-11&includeInactive=false")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .body(JsonNode.class));
        assertThat(activeOnly).doesNotContainKey(adultPersonalId);

        Map<UUID, JsonNode> withInactive = indexById(client()
                .get()
                .uri("/api/v1/recurring-expenses?month=2026-11&includeInactive=true")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .body(JsonNode.class));
        assertThat(withInactive).containsKey(adultPersonalId);
        assertThat(withInactive.get(adultPersonalId).path("isActive").asBoolean()).isFalse();
    }

    @Test
    void pendingDueBetweenReturnsUnpaidActiveExpensesAcrossAllFamiliesWithClampedDates() {
        // Family 1: one unpaid in range (dueDay 31 clamped to Nov 30), one paid in range, one inactive
        UUID fam1DueId = createRecurring(
                adultBearer,
                "Seguro Auto",
                adultAccountId,
                "FAMILY",
                new BigDecimal("80000"),
                "ARS",
                31,
                "MANUAL",
                "2026-01-01",
                null);
        UUID fam1PaidId = createRecurring(
                adultBearer,
                "Cable",
                adultAccountId,
                "FAMILY",
                new BigDecimal("25000"),
                "ARS",
                25,
                "MANUAL",
                "2026-01-01",
                null);
        UUID fam1InactiveId = createRecurring(
                adultBearer,
                "Viejo Club",
                adultAccountId,
                "FAMILY",
                new BigDecimal("10000"),
                "ARS",
                26,
                "MANUAL",
                "2026-01-01",
                null);

        // Pay Cable in 2026-11
        client().post()
                .uri("/api/v1/recurring-expenses/" + fam1PaidId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("month", "2026-11", "amount", new BigDecimal("25000")))
                .retrieve()
                .toBodilessEntity();

        // Deactivate Viejo Club
        client().delete()
                .uri("/api/v1/recurring-expenses/" + fam1InactiveId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toBodilessEntity();

        // Family 2: active unpaid expense due on 2026-11-28
        UUID family2Id = fixtures.createFamily("Familia Two " + UUID.randomUUID());
        UUID adult2Id = fixtures.createUser(
                family2Id,
                "adult-fam2-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID fam2AccountId = fixtures.createAccount(family2Id, adult2Id, "BANK", "ARS");
        UUID fam2CategoryId = insertExpenseCategory(family2Id, "Hogar " + UUID.randomUUID());
        String fam2Bearer =
                tokens.bearer(adult2Id, family2Id, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        ResponseEntity<JsonNode> fam2Create = client()
                .post()
                .uri("/api/v1/recurring-expenses")
                .header(HttpHeaders.AUTHORIZATION, fam2Bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Agua",
                        "categoryId", fam2CategoryId.toString(),
                        "accountId", fam2AccountId.toString(),
                        "baseAmount", new BigDecimal("18000"),
                        "currencyCode", "ARS",
                        "dueDay", 28,
                        "adjustmentRule", "MANUAL",
                        "startDate", "2026-01-01"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(fam2Create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID fam2DueId = UUID.fromString(fam2Create.getBody().path("id").asText());

        List<RecurringDue> dues = recurringExpenseQueryApi.pendingDueBetween(
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 30));

        List<RecurringDue> testDues = dues.stream()
                .filter(d -> d.familyId().equals(familyId) || d.familyId().equals(family2Id))
                .toList();

        assertThat(testDues).hasSize(2);
        assertThat(testDues.get(0).recurringExpenseId()).isEqualTo(fam2DueId);
        assertThat(testDues.get(0).familyId()).isEqualTo(family2Id);
        assertThat(testDues.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 11, 28));
        assertThat(testDues.get(0).estimatedAmount()).isEqualByComparingTo("18000.0000");

        assertThat(testDues.get(1).recurringExpenseId()).isEqualTo(fam1DueId);
        assertThat(testDues.get(1).familyId()).isEqualTo(familyId);
        assertThat(testDues.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(testDues.get(1).estimatedAmount()).isEqualByComparingTo("80000.0000");
    }

    private UUID createRecurring(
            String bearer,
            String name,
            UUID accountId,
            String scope,
            BigDecimal baseAmount,
            String currencyCode,
            int dueDay,
            String adjustmentRule,
            String startDate,
            String endDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryId", categoryId.toString());
        if (accountId != null) {
            body.put("accountId", accountId.toString());
        }
        if (scope != null) {
            body.put("scope", scope);
        }
        body.put("baseAmount", baseAmount);
        body.put("currencyCode", currencyCode);
        body.put("dueDay", dueDay);
        body.put("adjustmentRule", adjustmentRule);
        body.put("startDate", startDate);
        if (endDate != null) {
            body.put("endDate", endDate);
        }

        ResponseEntity<JsonNode> resp = client()
                .post()
                .uri("/api/v1/recurring-expenses")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        return UUID.fromString(resp.getBody().path("id").asText());
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
                VALUES (?, ?, ?, 'EXPENSE', 'receipt', '#2563EB', FALSE, TRUE)
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
