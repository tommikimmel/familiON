package com.familion.finance.installments.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.finance.installments.application.InstallmentPostingService;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
class InstallmentPlanControllerIT extends IntegrationTestBase {

    private static final ZoneId AR_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private InstallmentPostingService postingService;

    private UUID familyId;
    private UUID adultId;
    private UUID childOwnId;
    private UUID childSummaryId;
    private UUID otherFamilyId;
    private UUID otherAdultId;

    private UUID cardAccountId;
    private UUID childCardAccountId;
    private UUID bankAccountId;
    private UUID expenseCategoryId;

    private String adultBearer;
    private String childOwnBearer;
    private String childSummaryBearer;
    private String otherAdultBearer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM installment_plans");
        jdbcTemplate.update("DELETE FROM economic_indices");
        clearEconCache();

        familyId = fixtures.createFamily("Familia Cuotas " + UUID.randomUUID());
        adultId = fixtures.createUser(
                familyId,
                "adult-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        childOwnId = fixtures.createUser(
                familyId,
                "child-own-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);
        childSummaryId = fixtures.createUser(
                familyId,
                "child-sum-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.FAMILY_SUMMARY);

        otherFamilyId = fixtures.createFamily("Otra Familia " + UUID.randomUUID());
        otherAdultId = fixtures.createUser(
                otherFamilyId,
                "other-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);

        cardAccountId = createCreditCardAccount(familyId, adultId, "Visa Familia", "ARS", 25, 1);
        childCardAccountId =
                createCreditCardAccount(familyId, childOwnId, "Visa Hijo", "ARS", 25, 5);
        bankAccountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        expenseCategoryId = createExpenseCategory(familyId, "Tecnologia");

        adultBearer = tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childOwnBearer =
                tokens.bearer(childOwnId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        childSummaryBearer =
                tokens.bearer(childSummaryId, familyId, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        otherAdultBearer =
                tokens.bearer(otherAdultId, otherFamilyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    }

    @Test
    void createSixInstallmentPlanProjectsTransactionsComputesMetricsAndRespectsExactSum() {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(AR_ZONE));
        LocalDate purchaseDate = currentMonth.minusMonths(2).atDay(20);

        // Create plan without IPC first -> realRemainingAmount should be null
        ResponseEntity<JsonNode> createResp = post(
                "/api/v1/installment-plans",
                Map.ofEntries(
                        Map.entry("description", "Notebook Pro"),
                        Map.entry("categoryId", expenseCategoryId.toString()),
                        Map.entry("creditCardAccountId", cardAccountId.toString()),
                        Map.entry("currencyCode", "ARS"),
                        Map.entry("totalInstallments", 6),
                        Map.entry("amountMode", "TOTAL"),
                        Map.entry("amount", "1000.00"),
                        Map.entry("hasInterest", true),
                        Map.entry("cashPrice", "800.00"),
                        Map.entry("purchaseDate", purchaseDate.toString())),
                adultBearer);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = createResp.getBody();
        assertThat(created).isNotNull();
        UUID planId = UUID.fromString(created.path("id").asText());
        assertThat(created.path("description").asText()).isEqualTo("Notebook Pro");
        assertThat(created.path("totalInstallments").asInt()).isEqualTo(6);
        assertThat(created.path("paidInstallments").asInt()).isEqualTo(2);
        assertThat(created.path("hasInterest").asBoolean()).isTrue();
        assertThat(created.path("cashPrice").asText()).isEqualTo("800.0000");
        assertThat(created.path("financedTotal").asText()).isEqualTo("1000.0000");
        assertThat(created.path("installmentAmount").asText()).isEqualTo("166.6667");
        assertThat(created.path("surchargeAmount").asText()).isEqualTo("200.0000");
        assertThat(created.path("surchargePct").asText()).isEqualTo("25.0000");
        assertThat(created.path("firstBillingDate").asText())
                .isEqualTo(currentMonth.minusMonths(1).atDay(1).toString());
        assertThat(created.path("lastBillingDate").asText())
                .isEqualTo(currentMonth.plusMonths(4).atDay(1).toString());
        assertThat(created.path("realRemainingAmount").isNull()).isTrue();
        assertThat(created.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.path("nextInstallment").path("number").asInt()).isEqualTo(3);
        assertThat(created.path("nextInstallment").path("date").asText())
                .isEqualTo(currentMonth.plusMonths(1).atDay(1).toString());

        // Verify detail endpoint returns 6 installments with exact statuses and exact sum
        ResponseEntity<JsonNode> detailResp =
                get("/api/v1/installment-plans/" + planId, adultBearer);
        assertThat(detailResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode installments = detailResp.getBody().path("installments");
        assertThat(installments).hasSize(6);

        List<String> statuses = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode item : installments) {
            statuses.add(item.path("status").asText());
            sum = sum.add(new BigDecimal(item.path("amount").asText()));
        }
        assertThat(statuses)
                .containsExactly(
                        "POSTED", "POSTED", "PROJECTED", "PROJECTED", "PROJECTED", "PROJECTED");
        assertThat(sum).isEqualByComparingTo("1000.0000");

        // Verify 6 transactions persisted in DB with economic snapshots
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE installment_plan_id = ? AND deleted_at IS NULL",
                Integer.class,
                planId);
        assertThat(txCount).isEqualTo(6);
        Integer snapCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM economic_snapshots s
                JOIN transactions t ON t.id = s.transaction_id
                WHERE t.installment_plan_id = ?
                """,
                Integer.class,
                planId);
        assertThat(snapCount).isEqualTo(6);

        // Insert IPC_MENSUAL and verify realRemainingAmount reflects inflation discount
        insertIndex("IPC_MENSUAL", currentMonth.atDay(1), new BigDecimal("10.000000"));
        clearEconCache();
        ResponseEntity<JsonNode> detailWithIpc =
                get("/api/v1/installment-plans/" + planId, adultBearer);
        BigDecimal remaining = new BigDecimal(detailWithIpc.getBody().path("remainingAmount").asText());
        BigDecimal realRemaining =
                new BigDecimal(detailWithIpc.getBody().path("realRemainingAmount").asText());
        assertThat(remaining).isEqualByComparingTo("666.6666");
        assertThat(realRemaining).isLessThan(remaining);
        assertThat(realRemaining).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void rejectsNonCreditCardWith422AndChildAttributingToOtherWith403() {
        LocalDate purchaseDate = LocalDate.now(AR_ZONE);

        ResponseEntity<JsonNode> nonCardResp = post(
                "/api/v1/installment-plans",
                Map.of(
                        "description", "Invalid Bank Plan",
                        "categoryId", expenseCategoryId.toString(),
                        "creditCardAccountId", bankAccountId.toString(),
                        "currencyCode", "ARS",
                        "totalInstallments", 3,
                        "amountMode", "TOTAL",
                        "amount", "30000",
                        "hasInterest", false,
                        "purchaseDate", purchaseDate.toString()),
                adultBearer);
        assertThat(nonCardResp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<JsonNode> childAttribResp = post(
                "/api/v1/installment-plans",
                Map.of(
                        "description", "Unauthorized Attribution",
                        "categoryId", expenseCategoryId.toString(),
                        "creditCardAccountId", childCardAccountId.toString(),
                        "currencyCode", "ARS",
                        "totalInstallments", 3,
                        "amountMode", "TOTAL",
                        "amount", "30000",
                        "hasInterest", false,
                        "purchaseDate", purchaseDate.toString(),
                        "attributedToUserId", adultId.toString()),
                childOwnBearer);
        assertThat(childAttribResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void schedulerPostsDueInstallmentsAndCompletesPlanIdempotentlyUsingFixedClock() {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(AR_ZONE));
        LocalDate futurePurchase = currentMonth.plusMonths(1).atDay(20);
        // closing 25, due 1 -> installments on currentMonth+2, +3, +4 (all PROJECTED)
        ResponseEntity<JsonNode> createResp = post(
                "/api/v1/installment-plans",
                Map.of(
                        "description", "Air Conditioner",
                        "categoryId", expenseCategoryId.toString(),
                        "creditCardAccountId", cardAccountId.toString(),
                        "currencyCode", "ARS",
                        "totalInstallments", 3,
                        "amountMode", "PER_INSTALLMENT",
                        "amount", "50000",
                        "hasInterest", false,
                        "purchaseDate", futurePurchase.toString()),
                adultBearer);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID planId = UUID.fromString(createResp.getBody().path("id").asText());
        assertThat(createResp.getBody().path("paidInstallments").asInt()).isZero();

        LocalDate secondBillingDate = currentMonth.plusMonths(3).atDay(1);
        LocalDate thirdBillingDate = currentMonth.plusMonths(4).atDay(1);

        Clock clockAtSecond =
                Clock.fixed(secondBillingDate.atTime(6, 15).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        InstallmentPostingService.PostingResult firstRun =
                postingService.postDueInstallments(clockAtSecond);
        assertThat(firstRun.postedInstallments()).isEqualTo(2);
        assertThat(firstRun.completedPlans()).isZero();

        // Idempotent repeat on same date
        InstallmentPostingService.PostingResult repeatFirst =
                postingService.postDueInstallments(clockAtSecond);
        assertThat(repeatFirst.postedInstallments()).isZero();
        assertThat(repeatFirst.completedPlans()).isZero();

        JsonNode afterSecond = get("/api/v1/installment-plans/" + planId, adultBearer).getBody();
        assertThat(afterSecond.path("paidInstallments").asInt()).isEqualTo(2);
        assertThat(afterSecond.path("status").asText()).isEqualTo("ACTIVE");

        Clock clockAtThird =
                Clock.fixed(thirdBillingDate.atTime(6, 15).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        InstallmentPostingService.PostingResult finalRun =
                postingService.postDueInstallments(clockAtThird);
        assertThat(finalRun.postedInstallments()).isEqualTo(1);
        assertThat(finalRun.completedPlans()).isEqualTo(1);

        InstallmentPostingService.PostingResult repeatFinal =
                postingService.postDueInstallments(clockAtThird);
        assertThat(repeatFinal.postedInstallments()).isZero();
        assertThat(repeatFinal.completedPlans()).isZero();

        JsonNode afterFinal = get("/api/v1/installment-plans/" + planId, adultBearer).getBody();
        assertThat(afterFinal.path("paidInstallments").asInt()).isEqualTo(3);
        assertThat(afterFinal.path("remainingAmount").asText()).isEqualTo("0.0000");
        assertThat(afterFinal.path("nextInstallment").isNull()).isTrue();
        assertThat(afterFinal.path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void cancelAndPrepayRemainingInstallmentsEnforcePermissionsAndConflictOnClosedPlan() {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(AR_ZONE));
        LocalDate purchaseDate = currentMonth.minusMonths(1).atDay(20);
        // 1st installment currentMonth-01 (POSTED), 2nd & 3rd PROJECTED

        UUID cancelPlanId = UUID.fromString(post(
                        "/api/v1/installment-plans",
                        Map.of(
                                "description", "Cancelable Plan",
                                "categoryId", expenseCategoryId.toString(),
                                "creditCardAccountId", cardAccountId.toString(),
                                "currencyCode", "ARS",
                                "totalInstallments", 3,
                                "amountMode", "PER_INSTALLMENT",
                                "amount", "10000",
                                "hasInterest", false,
                                "purchaseDate", purchaseDate.toString()),
                        adultBearer)
                .getBody()
                .path("id")
                .asText());

        // Non-author child cannot cancel adult's plan
        ResponseEntity<JsonNode> forbiddenCancel = post(
                "/api/v1/installment-plans/" + cancelPlanId + "/cancel-remaining",
                Map.of("mode", "CANCEL"),
                childOwnBearer);
        assertThat(forbiddenCancel.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> cancelResp = post(
                "/api/v1/installment-plans/" + cancelPlanId + "/cancel-remaining",
                Map.of("mode", "CANCEL"),
                adultBearer);
        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResp.getBody().path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelResp.getBody().path("paidInstallments").asInt()).isEqualTo(1);
        assertThat(cancelResp.getBody().path("remainingAmount").asText()).isEqualTo("0.0000");
        assertThat(cancelResp.getBody().path("nextInstallment").isNull()).isTrue();

        JsonNode cancelledDetail =
                get("/api/v1/installment-plans/" + cancelPlanId, adultBearer).getBody();
        List<String> cancelStatuses = new ArrayList<>();
        cancelledDetail.path("installments").forEach(n -> cancelStatuses.add(n.path("status").asText()));
        assertThat(cancelStatuses).containsExactly("POSTED", "CANCELLED", "CANCELLED");

        // Second cancel returns 409 Conflict
        ResponseEntity<JsonNode> conflictCancel = post(
                "/api/v1/installment-plans/" + cancelPlanId + "/cancel-remaining",
                Map.of("mode", "CANCEL"),
                adultBearer);
        assertThat(conflictCancel.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Prepay flow
        UUID prepayPlanId = UUID.fromString(post(
                        "/api/v1/installment-plans",
                        Map.of(
                                "description", "Prepayable Plan",
                                "categoryId", expenseCategoryId.toString(),
                                "creditCardAccountId", cardAccountId.toString(),
                                "currencyCode", "ARS",
                                "totalInstallments", 3,
                                "amountMode", "PER_INSTALLMENT",
                                "amount", "15000",
                                "hasInterest", false,
                                "purchaseDate", purchaseDate.toString()),
                        adultBearer)
                .getBody()
                .path("id")
                .asText());

        LocalDate prepayDate = LocalDate.now(AR_ZONE);
        ResponseEntity<JsonNode> prepayResp = post(
                "/api/v1/installment-plans/" + prepayPlanId + "/cancel-remaining",
                Map.of("mode", "PREPAY", "prepayDate", prepayDate.toString()),
                adultBearer);
        assertThat(prepayResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(prepayResp.getBody().path("status").asText()).isEqualTo("COMPLETED");
        assertThat(prepayResp.getBody().path("paidInstallments").asInt()).isEqualTo(3);
        assertThat(prepayResp.getBody().path("remainingAmount").asText()).isEqualTo("0.0000");

        JsonNode prepaidDetail =
                get("/api/v1/installment-plans/" + prepayPlanId, adultBearer).getBody();
        assertThat(prepaidDetail.path("installments").get(1).path("status").asText())
                .isEqualTo("POSTED");
        assertThat(prepaidDetail.path("installments").get(1).path("date").asText())
                .isEqualTo(prepayDate.toString());
        assertThat(prepaidDetail.path("installments").get(2).path("status").asText())
                .isEqualTo("POSTED");
        assertThat(prepaidDetail.path("installments").get(2).path("date").asText())
                .isEqualTo(prepayDate.toString());

        ResponseEntity<JsonNode> conflictPrepay = post(
                "/api/v1/installment-plans/" + prepayPlanId + "/cancel-remaining",
                Map.of("mode", "PREPAY"),
                adultBearer);
        assertThat(conflictPrepay.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void listsPlansByViewAndStatusIsolatesFamiliesAndGroupsMonthlyCommitment() {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(AR_ZONE));
        LocalDate purchaseDate = currentMonth.atDay(20);
        // closing 25, due 1 -> installments on currentMonth+1, +2 (both PROJECTED)

        // Plan 1: Adult ARS 2 x 10000 (attributed to adult)
        UUID adultPlanId = UUID.fromString(post(
                        "/api/v1/installment-plans",
                        Map.of(
                                "description", "Adult ARS Plan",
                                "categoryId", expenseCategoryId.toString(),
                                "creditCardAccountId", cardAccountId.toString(),
                                "currencyCode", "ARS",
                                "totalInstallments", 2,
                                "amountMode", "PER_INSTALLMENT",
                                "amount", "10000",
                                "hasInterest", false,
                                "purchaseDate", purchaseDate.toString()),
                        adultBearer)
                .getBody()
                .path("id")
                .asText());

        // Plan 2: Adult USD 2 x 100 (attributed to adult)
        post(
                "/api/v1/installment-plans",
                Map.of(
                        "description", "Adult USD Plan",
                        "categoryId", expenseCategoryId.toString(),
                        "creditCardAccountId", cardAccountId.toString(),
                        "currencyCode", "USD",
                        "totalInstallments", 2,
                        "amountMode", "PER_INSTALLMENT",
                        "amount", "100",
                        "hasInterest", false,
                        "purchaseDate", purchaseDate.toString()),
                adultBearer);

        // Plan 3: Adult pays ARS 2 x 5000 attributed to childOwn
        UUID childPlanId = UUID.fromString(post(
                        "/api/v1/installment-plans",
                        Map.of(
                                "description", "Child Bike",
                                "categoryId", expenseCategoryId.toString(),
                                "creditCardAccountId", cardAccountId.toString(),
                                "currencyCode", "ARS",
                                "totalInstallments", 2,
                                "amountMode", "PER_INSTALLMENT",
                                "amount", "5000",
                                "hasInterest", false,
                                "purchaseDate", purchaseDate.toString(),
                                "attributedToUserId", childOwnId.toString()),
                        adultBearer)
                .getBody()
                .path("id")
                .asText());

        // Personal view for childOwn shows only the plan attributed to childOwn
        ResponseEntity<JsonNode> childPersonalList =
                get("/api/v1/installment-plans?status=ACTIVE&view=PERSONAL", childOwnBearer);
        assertThat(childPersonalList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(childPersonalList.getBody()).hasSize(1);
        assertThat(childPersonalList.getBody().get(0).path("id").asText())
                .isEqualTo(childPlanId.toString());

        // Family view denied for OWN_ONLY child
        assertThat(get("/api/v1/installment-plans?view=FAMILY", childOwnBearer).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Adult personal view sees only adult's 2 plans; family view sees all 3
        assertThat(get("/api/v1/installment-plans?view=PERSONAL", adultBearer).getBody()).hasSize(2);
        assertThat(get("/api/v1/installment-plans?view=FAMILY", adultBearer).getBody()).hasSize(3);

        // Cross-family isolation
        assertThat(get("/api/v1/installment-plans/" + adultPlanId, otherAdultBearer).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/installment-plans?view=FAMILY", otherAdultBearer).getBody())
                .isEmpty();

        // Commitment grouped by month and currency
        String m1 = currentMonth.plusMonths(1).toString();
        String m2 = currentMonth.plusMonths(2).toString();

        ResponseEntity<JsonNode> familyCommitResp =
                get("/api/v1/installment-plans/commitment?months=12&view=FAMILY", adultBearer);
        assertThat(familyCommitResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode familyCommit = familyCommitResp.getBody();
        assertThat(familyCommit).hasSize(4);
        assertThat(familyCommit.get(0).path("month").asText()).isEqualTo(m1);
        assertThat(familyCommit.get(0).path("currencyCode").asText()).isEqualTo("ARS");
        assertThat(familyCommit.get(0).path("amount").asText()).isEqualTo("15000.0000");
        assertThat(familyCommit.get(1).path("month").asText()).isEqualTo(m1);
        assertThat(familyCommit.get(1).path("currencyCode").asText()).isEqualTo("USD");
        assertThat(familyCommit.get(1).path("amount").asText()).isEqualTo("100.0000");
        assertThat(familyCommit.get(2).path("month").asText()).isEqualTo(m2);
        assertThat(familyCommit.get(2).path("currencyCode").asText()).isEqualTo("ARS");
        assertThat(familyCommit.get(2).path("amount").asText()).isEqualTo("15000.0000");
        assertThat(familyCommit.get(3).path("month").asText()).isEqualTo(m2);
        assertThat(familyCommit.get(3).path("currencyCode").asText()).isEqualTo("USD");
        assertThat(familyCommit.get(3).path("amount").asText()).isEqualTo("100.0000");

        // Personal commitment for childOwn shows only 5000 ARS/month
        JsonNode childCommit =
                get("/api/v1/installment-plans/commitment?months=12&view=PERSONAL", childOwnBearer)
                        .getBody();
        assertThat(childCommit).hasSize(2);
        assertThat(childCommit.get(0).path("amount").asText()).isEqualTo("5000.0000");
        assertThat(childCommit.get(1).path("amount").asText()).isEqualTo("5000.0000");

        // Visibility for commitment totals: OWN_ONLY denied FAMILY, FAMILY_SUMMARY allowed FAMILY
        assertThat(get("/api/v1/installment-plans/commitment?months=12&view=FAMILY", childOwnBearer)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(
                                "/api/v1/installment-plans/commitment?months=12&view=FAMILY",
                                childSummaryBearer)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private UUID createCreditCardAccount(
            UUID familyId,
            UUID ownerUserId,
            String name,
            String currency,
            Integer closingDay,
            Integer dueDay) {
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    id, family_id, owner_user_id, name, account_type, currency_code,
                    initial_balance, closing_day, due_day, is_active
                )
                VALUES (?, ?, ?, ?, 'CREDIT_CARD', ?, 0, ?, ?, TRUE)
                """,
                accountId,
                familyId,
                ownerUserId,
                name,
                currency,
                closingDay,
                dueDay);
        return accountId;
    }

    private UUID createExpenseCategory(UUID familyId, String name) {
        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO categories (id, family_id, name, kind, icon, color_hex, is_system, is_active)
                VALUES (?, ?, ?, 'EXPENSE', 'shopping-bag', '#3B82F6', FALSE, TRUE)
                """,
                categoryId,
                familyId,
                name);
        return categoryId;
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

    private void clearEconCache() {
        Set<String> econKeys = redisTemplate.keys("econ:*");
        if (econKeys != null && !econKeys.isEmpty()) {
            redisTemplate.delete(econKeys);
        }
    }

    private ResponseEntity<JsonNode> get(String uri, String bearer) {
        return client()
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String uri, Object body, String bearer) {
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
}
