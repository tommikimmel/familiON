package com.familion.analytics.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.familion.analytics.application.AnalyticsQuery;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-"
        })
class AnalyticsControllerIT extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    private AnalyticsQuery analyticsQuery;

    private UUID familyId;
    private UUID adultId;
    private UUID childFullId;
    private UUID childSummaryId;
    private UUID childOwnId;

    private UUID adultArsAccountId;
    private UUID adultUsdAccountId;
    private UUID childArsAccountId;

    private UUID parentFoodId;
    private UUID childSuperId;
    private UUID parentTransportId;

    private String adultBearer;
    private String childFullBearer;
    private String childSummaryBearer;
    private String childOwnBearer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM economic_indices");
        clearRedisKeys("econ:*");
        clearRedisKeys("family:*:analytics:*");
        clearInvocations(analyticsQuery);

        seedDataset();
    }

    @Test
    void summaryCalculatesTotalsPerViewAttributionNoDoubleCountingAndMetrics() {
        // Adult PERSONAL (Nov 2026)
        ResponseEntity<JsonNode> adultPersonal = get(
                "/api/v1/analytics/summary?view=PERSONAL&lens=NOMINAL&granularity=MONTH&date=2026-11-23",
                adultBearer);
        assertThat(adultPersonal.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = adultPersonal.getBody();
        assertThat(body.path("period").path("granularity").asText()).isEqualTo("MONTH");
        assertThat(body.path("period").path("from").asText()).isEqualTo("2026-11-01");
        assertThat(body.path("period").path("to").asText()).isEqualTo("2026-11-30");
        assertThat(body.path("period").path("label").asText()).isEqualTo("Noviembre 2026");
        assertThat(body.path("view").asText()).isEqualTo("PERSONAL");
        assertThat(body.path("lens").asText()).isEqualTo("NOMINAL");
        assertThat(body.path("unit").asText()).isEqualTo("ARS");
        assertThat(body.path("income").asText()).isEqualTo("200000.0000");
        assertThat(body.path("expense").asText()).isEqualTo("70000.0000");
        assertThat(body.path("balance").asText()).isEqualTo("130000.0000");
        assertThat(body.path("savingsRatePct").asText()).isEqualTo("65.0000");
        assertThat(body.path("installmentCommitmentPct").asText()).isEqualTo("10.0000");
        assertThat(body.path("previous").path("income").asText()).isEqualTo("120000.0000");
        assertThat(body.path("previous").path("expense").asText()).isEqualTo("55000.0000");
        assertThat(body.path("realVariationPct").asText()).isEqualTo("15.7025");
        assertThat(body.path("lensApproximated").asBoolean()).isFalse();

        // Child PERSONAL (counts ALLOWANCE_IN + attributed expense paid by adult)
        ResponseEntity<JsonNode> childPersonal = get(
                "/api/v1/analytics/summary?view=PERSONAL&lens=NOMINAL&granularity=MONTH&date=2026-11-23",
                childFullBearer);
        assertThat(childPersonal.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode childBody = childPersonal.getBody();
        assertThat(childBody.path("income").asText()).isEqualTo("30000.0000");
        assertThat(childBody.path("expense").asText()).isEqualTo("15000.0000");
        assertThat(childBody.path("balance").asText()).isEqualTo("15000.0000");
        assertThat(childBody.path("savingsRatePct").asText()).isEqualTo("50.0000");
        assertThat(childBody.path("installmentCommitmentPct").asText()).isEqualTo("33.3333");

        // FAMILY view (ALLOWANCE_IN excluded -> no double counting; sums adult + child expenses)
        ResponseEntity<JsonNode> familyResp = get(
                "/api/v1/analytics/summary?view=FAMILY&lens=NOMINAL&granularity=MONTH&date=2026-11-23",
                adultBearer);
        assertThat(familyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode famBody = familyResp.getBody();
        assertThat(famBody.path("income").asText()).isEqualTo("200000.0000");
        assertThat(famBody.path("expense").asText()).isEqualTo("85000.0000");
        assertThat(famBody.path("balance").asText()).isEqualTo("115000.0000");
        assertThat(famBody.path("savingsRatePct").asText()).isEqualTo("57.5000");
        assertThat(famBody.path("installmentCommitmentPct").asText()).isEqualTo("15.0000");
    }

    @Test
    void lensesCalculateConstantBaseApproximatedReferenceAndIncomePct() {
        // CONSTANT_BASE with IPC on Oct 2026 (IPC 110 -> latest 121 => x1.1)
        ResponseEntity<JsonNode> constantOct = get(
                "/api/v1/analytics/summary?view=PERSONAL&lens=CONSTANT_BASE&granularity=MONTH&date=2026-10-15",
                adultBearer);
        assertThat(constantOct.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(constantOct.getBody().path("unit").asText()).isEqualTo("ARS (hoy)");
        assertThat(constantOct.getBody().path("income").asText()).isEqualTo("132000.0000");
        assertThat(constantOct.getBody().path("expense").asText()).isEqualTo("60500.0000");
        assertThat(constantOct.getBody().path("balance").asText()).isEqualTo("71500.0000");
        assertThat(constantOct.getBody().path("lensApproximated").asBoolean()).isFalse();

        // REFERENCE_CURRENCY on Nov 2026 FAMILY
        ResponseEntity<JsonNode> referenceNov = get(
                "/api/v1/analytics/summary?view=FAMILY&lens=REFERENCE_CURRENCY&granularity=MONTH&date=2026-11-23",
                adultBearer);
        assertThat(referenceNov.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(referenceNov.getBody().path("unit").asText()).isEqualTo("USD");
        assertThat(referenceNov.getBody().path("income").asText()).isEqualTo("200.0000");
        assertThat(referenceNov.getBody().path("expense").asText()).isEqualTo("85.0000");
        assertThat(referenceNov.getBody().path("balance").asText()).isEqualTo("115.0000");

        // INCOME_PCT on Nov 2026 FAMILY
        ResponseEntity<JsonNode> incomePctNov = get(
                "/api/v1/analytics/summary?view=FAMILY&lens=INCOME_PCT&granularity=MONTH&date=2026-11-23",
                adultBearer);
        assertThat(incomePctNov.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(incomePctNov.getBody().path("unit").asText()).isEqualTo("%");
        assertThat(incomePctNov.getBody().path("income").asText()).isEqualTo("100.0000");
        assertThat(incomePctNov.getBody().path("expense").asText()).isEqualTo("42.5000");
        assertThat(incomePctNov.getBody().path("balance").asText()).isEqualTo("57.5000");

        // INCOME_PCT when period has 0 income -> null values
        ResponseEntity<JsonNode> incomePctZero = get(
                "/api/v1/analytics/summary?view=FAMILY&lens=INCOME_PCT&granularity=MONTH&date=2026-01-15",
                adultBearer);
        assertThat(incomePctZero.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(incomePctZero.getBody().path("income").isNull()).isTrue();
        assertThat(incomePctZero.getBody().path("expense").isNull()).isTrue();
        assertThat(incomePctZero.getBody().path("balance").isNull()).isTrue();

        // CONSTANT_BASE without IPC -> falls back to nominal and lensApproximated = true
        jdbcTemplate.update("DELETE FROM economic_indices WHERE index_code = 'IPC_ACUMULADO'");
        clearRedisKeys("econ:*");
        clearRedisKeys("family:*:analytics:*");

        ResponseEntity<JsonNode> approxOct = get(
                "/api/v1/analytics/summary?view=PERSONAL&lens=CONSTANT_BASE&granularity=MONTH&date=2026-10-15",
                adultBearer);
        assertThat(approxOct.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approxOct.getBody().path("income").asText()).isEqualTo("120000.0000");
        assertThat(approxOct.getBody().path("expense").asText()).isEqualTo("55000.0000");
        assertThat(approxOct.getBody().path("lensApproximated").asBoolean()).isTrue();
    }

    @Test
    void byCategoryRollsUpToParentAndIncludesUncategorizedAndVariation() {
        ResponseEntity<JsonNode> resp = get(
                "/api/v1/analytics/by-category?view=FAMILY&lens=NOMINAL&granularity=MONTH&date=2026-11-23",
                adultBearer);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = resp.getBody();
        assertThat(body.path("total").asText()).isEqualTo("85000.0000");
        JsonNode slices = body.path("slices");
        assertThat(slices).hasSize(3);

        // 1. Alimentación (40000 from childSuper + 20000 USD->ARS directly on parentFood = 60000)
        // Previous month (Oct) Alimentación CONSTANT_BASE = 60500 -> variation = (60000 - 60500)/60500*100 = -0.8264
        JsonNode food = slices.get(0);
        assertThat(food.path("categoryId").asText()).isEqualTo(parentFoodId.toString());
        assertThat(food.path("name").asText()).isEqualTo("Alimentación");
        assertThat(food.path("colorHex").asText()).isEqualTo("#10B981");
        assertThat(food.path("icon").asText()).isEqualTo("utensils");
        assertThat(food.path("amount").asText()).isEqualTo("60000.0000");
        assertThat(food.path("pct").asText()).isEqualTo("70.5882");
        assertThat(food.path("variationPct").asText()).isEqualTo("-0.8264");

        // 2. Transporte (15000)
        JsonNode transport = slices.get(1);
        assertThat(transport.path("categoryId").asText()).isEqualTo(parentTransportId.toString());
        assertThat(transport.path("name").asText()).isEqualTo("Transporte");
        assertThat(transport.path("amount").asText()).isEqualTo("15000.0000");
        assertThat(transport.path("variationPct").isNull()).isTrue();

        // 3. Sin categoría (10000)
        JsonNode uncat = slices.get(2);
        assertThat(uncat.path("categoryId").isNull()).isTrue();
        assertThat(uncat.path("name").asText()).isEqualTo("Sin categoría");
        assertThat(uncat.path("colorHex").isNull()).isTrue();
        assertThat(uncat.path("icon").isNull()).isTrue();
        assertThat(uncat.path("amount").asText()).isEqualTo("10000.0000");
    }

    @Test
    void byPersonAndVisibilityPoliciesAreEnforced() {
        // Adult gets by-person with attributed expenses (adult: 70000, childFull: 15000)
        ResponseEntity<JsonNode> byPerson = get(
                "/api/v1/analytics/by-person?view=FAMILY&lens=NOMINAL&granularity=MONTH&date=2026-11-23",
                adultBearer);
        assertThat(byPerson.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byPerson.getBody().path("total").asText()).isEqualTo("85000.0000");
        JsonNode slices = byPerson.getBody().path("slices");
        assertThat(slices).hasSize(2);
        assertThat(slices.get(0).path("userId").asText()).isEqualTo(adultId.toString());
        assertThat(slices.get(0).path("fullName").asText()).isEqualTo("Ana Adulto");
        assertThat(slices.get(0).path("accentColor").asText()).isEqualTo("#0F766E");
        assertThat(slices.get(0).path("amount").asText()).isEqualTo("70000.0000");
        assertThat(slices.get(1).path("userId").asText()).isEqualTo(childFullId.toString());
        assertThat(slices.get(1).path("fullName").asText()).isEqualTo("Lauti Hijo");
        assertThat(slices.get(1).path("accentColor").asText()).isEqualTo("#2563EB");
        assertThat(slices.get(1).path("amount").asText()).isEqualTo("15000.0000");

        // by-person with view=PERSONAL -> 400
        ResponseEntity<JsonNode> personalByPerson = get(
                "/api/v1/analytics/by-person?view=PERSONAL&date=2026-11-23", adultBearer);
        assertThat(personalByPerson.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // FAMILY_SUMMARY child: summary FAMILY -> 200 OK; by-person -> 403
        ResponseEntity<JsonNode> summaryForChildSummary = get(
                "/api/v1/analytics/summary?view=FAMILY&date=2026-11-23", childSummaryBearer);
        assertThat(summaryForChildSummary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summaryForChildSummary.getBody().path("expense").asText()).isEqualTo("85000.0000");

        ResponseEntity<JsonNode> byPersonForChildSummary = get(
                "/api/v1/analytics/by-person?view=FAMILY&date=2026-11-23", childSummaryBearer);
        assertThat(byPersonForChildSummary.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // OWN_ONLY child: summary FAMILY -> 403; by-category FAMILY -> 403; by-person -> 403
        assertThat(get("/api/v1/analytics/summary?view=FAMILY&date=2026-11-23", childOwnBearer)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/analytics/by-category?view=FAMILY&date=2026-11-23", childOwnBearer)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/analytics/by-person?view=FAMILY&date=2026-11-23", childOwnBearer)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void seriesReturnsTwelveMonthsWithZerosAndIsoWeekLabels() {
        ResponseEntity<JsonNode> monthly = get(
                "/api/v1/analytics/series?view=FAMILY&lens=NOMINAL&granularity=MONTH&date=2026-11-23&periods=12",
                adultBearer);
        assertThat(monthly.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode points = monthly.getBody().path("points");
        assertThat(points).hasSize(12);
        // Dec 2025..Aug 2026 empty -> 0.0000
        assertThat(points.get(0).path("periodStart").asText()).isEqualTo("2025-12-01");
        assertThat(points.get(0).path("label").asText()).isEqualTo("Diciembre 2025");
        assertThat(points.get(0).path("income").asText()).isEqualTo("0.0000");
        assertThat(points.get(0).path("expense").asText()).isEqualTo("0.0000");
        assertThat(points.get(0).path("balance").asText()).isEqualTo("0.0000");

        // Nov 2026
        assertThat(points.get(11).path("periodStart").asText()).isEqualTo("2026-11-01");
        assertThat(points.get(11).path("label").asText()).isEqualTo("Noviembre 2026");
        assertThat(points.get(11).path("income").asText()).isEqualTo("200000.0000");
        assertThat(points.get(11).path("expense").asText()).isEqualTo("85000.0000");
        assertThat(points.get(11).path("balance").asText()).isEqualTo("115000.0000");

        // WEEK granularity ISO labels
        ResponseEntity<JsonNode> weekly = get(
                "/api/v1/analytics/series?view=FAMILY&lens=NOMINAL&granularity=WEEK&date=2026-11-18&periods=2",
                adultBearer);
        assertThat(weekly.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode weekPoints = weekly.getBody().path("points");
        assertThat(weekPoints).hasSize(2);
        assertThat(weekPoints.get(0).path("periodStart").asText()).isEqualTo("2026-11-09");
        assertThat(weekPoints.get(0).path("label").asText()).isEqualTo("Semana 46 · 2026");
        assertThat(weekPoints.get(0).path("expense").asText()).isEqualTo("70000.0000");
        assertThat(weekPoints.get(1).path("periodStart").asText()).isEqualTo("2026-11-16");
        assertThat(weekPoints.get(1).path("label").asText()).isEqualTo("Semana 47 · 2026");
        assertThat(weekPoints.get(1).path("expense").asText()).isEqualTo("15000.0000");

        // Out-of-bounds periods -> 400
        assertThat(get("/api/v1/analytics/series?periods=25&date=2026-11-23", adultBearer)
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void calendarIncludesUnpaidRecurringVehicleInstallmentAllowanceAndValidatesRange() {
        // Seed recurring expenses (one paid in Nov, one unpaid with due_day=31 clamped to Nov 30)
        UUID unpaidRecurringId = UUID.randomUUID();
        UUID paidRecurringId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO recurring_expenses (
                    id, family_id, created_by_user_id, category_id, name, scope, base_amount,
                    currency_code, due_day, adjustment_rule, start_date, is_active
                ) VALUES (?, ?, ?, ?, 'Expensas', 'FAMILY', 95000.0000, 'ARS', 31, 'MANUAL', '2026-01-01', TRUE),
                         (?, ?, ?, ?, 'Luz', 'FAMILY', 18000.0000, 'ARS', 20, 'MANUAL', '2026-01-01', TRUE)
                """,
                unpaidRecurringId,
                familyId,
                adultId,
                parentFoodId,
                paidRecurringId,
                familyId,
                adultId,
                parentFoodId);

        // Pay "Luz" in Nov 2026 so it MUST be omitted from RECURRING_DUE
        UUID paidTxId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id, family_id, user_id, attributed_to_user_id, account_id, category_id,
                    type, scope, status, amount, currency_code, transaction_date, description, recurring_expense_id
                ) VALUES (?, ?, ?, ?, ?, ?, 'EXPENSE', 'FAMILY', 'POSTED', 18000.0000, 'ARS', '2026-11-19', 'Pago Luz', ?)
                """,
                paidTxId,
                familyId,
                adultId,
                adultId,
                adultArsAccountId,
                parentFoodId,
                paidRecurringId);
        insertSnapshot(paidTxId, "18000.0000", "18.0000", "121.000000", LocalDate.of(2026, 11, 19));

        // Seed vehicle with VTV on Nov 25, insurance on Nov 28, and two OIL_CHANGE logs
        // (older log has next_due_date Nov 22, newer log supersedes with next_due_date Nov 29)
        UUID vehicleId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO vehicles (
                    id, family_id, nickname, brand, model, model_year, license_plate,
                    primary_fuel_type, current_odometer_km, vtv_expiration_date, insurance_expiration, is_active
                ) VALUES (?, ?, 'Corolla', 'Toyota', 'Corolla', 2022, 'AF123CD',
                          'NAFTA_SUPER', 45000, '2026-11-25', '2026-11-28', TRUE)
                """,
                vehicleId,
                familyId);
        UUID oldMaintId = UUID.randomUUID();
        UUID latestMaintId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO maintenance_logs (
                    id, family_id, vehicle_id, user_id, maintenance_type, maintenance_date,
                    odometer_km, description, cost_amount, currency_code, next_due_date
                ) VALUES (?, ?, ?, ?, 'OIL_CHANGE', '2026-05-01', 35000, 'Cambio aceite viejo', 50000, 'ARS', '2026-11-22'),
                         (?, ?, ?, ?, 'OIL_CHANGE', '2026-10-01', 42000, 'Cambio aceite 50k', 60000, 'ARS', '2026-11-29')
                """,
                oldMaintId,
                familyId,
                vehicleId,
                adultId,
                latestMaintId,
                familyId,
                vehicleId,
                adultId);

        // Seed monthly allowance on day 5
        UUID allowanceId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO allowances (
                    id, family_id, assigned_by_user_id, child_user_id, target_account_id,
                    amount, currency_code, frequency, day_of_period, is_active
                ) VALUES (?, ?, ?, ?, ?, 30000.0000, 'ARS', 'MONTHLY', 5, TRUE)
                """,
                allowanceId,
                familyId,
                adultId,
                childFullId,
                childArsAccountId);

        // Range > 62 days -> 400 Bad Request
        ResponseEntity<JsonNode> tooWide = get(
                "/api/v1/analytics/calendar?from=2026-07-01&to=2026-09-01&view=FAMILY", adultBearer);
        assertThat(tooWide.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // FAMILY calendar for Nov 1..Dec 20 (50 days)
        ResponseEntity<JsonNode> famCal = get(
                "/api/v1/analytics/calendar?from=2026-11-01&to=2026-12-20&view=FAMILY", adultBearer);
        assertThat(famCal.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode days = famCal.getBody().path("days");
        assertThat(days).hasSize(50);

        List<JsonNode> allItems = new ArrayList<>();
        for (JsonNode day : days) {
            for (JsonNode item : day.path("items")) {
                allItems.add(item);
            }
        }

        // Unpaid Expensas clamped to Nov 30 is present; paid Luz is NOT present as RECURRING_DUE in Nov
        JsonNode nov30 = findDay(days, "2026-11-30");
        assertThat(hasItem(nov30, "RECURRING_DUE", unpaidRecurringId)).isTrue();
        JsonNode nov20 = findDay(days, "2026-11-20");
        assertThat(hasItem(nov20, "RECURRING_DUE", paidRecurringId)).isFalse();

        // Vehicle dues: VTV on Nov 25, Seguro on Nov 28, latest maintenance on Nov 29; superseded Nov 22 omitted
        assertThat(hasItem(findDay(days, "2026-11-25"), "VEHICLE_DUE", vehicleId)).isTrue();
        assertThat(hasItem(findDay(days, "2026-11-28"), "VEHICLE_DUE", vehicleId)).isTrue();
        assertThat(hasItem(findDay(days, "2026-11-29"), "VEHICLE_DUE", latestMaintId)).isTrue();
        assertThat(hasItem(findDay(days, "2026-11-22"), "VEHICLE_DUE", oldMaintId)).isFalse();

        // Allowance on Nov 5 & Dec 5; projected installment on Dec 15
        assertThat(hasItem(findDay(days, "2026-11-05"), "ALLOWANCE", allowanceId)).isTrue();
        assertThat(hasItem(findDay(days, "2026-12-05"), "ALLOWANCE", allowanceId)).isTrue();
        assertThat(findDay(days, "2026-12-15").path("items").toString()).contains("INSTALLMENT");

        // PERSONAL view hides family vehicle dues & family recurring dues
        ResponseEntity<JsonNode> personalCal = get(
                "/api/v1/analytics/calendar?from=2026-11-25&to=2026-11-30&view=PERSONAL",
                adultBearer);
        assertThat(personalCal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(personalCal.getBody().toString()).doesNotContain("VEHICLE_DUE");
        assertThat(personalCal.getBody().toString()).doesNotContain("Expensas");

        // FAMILY_SUMMARY child cannot access FAMILY calendar -> 403
        assertThat(get(
                                "/api/v1/analytics/calendar?from=2026-11-01&to=2026-11-10&view=FAMILY",
                                childSummaryBearer)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cacheAvoidsSecondDatabaseQueryAndInvalidatesOnTransactionCreation() {
        String uri = "/api/v1/analytics/summary?view=PERSONAL&lens=NOMINAL&granularity=MONTH&date=2026-11-23";

        // 1st call hits DB and populates Redis
        ResponseEntity<JsonNode> first = get(uri, adultBearer);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().path("expense").asText()).isEqualTo("70000.0000");

        verify(analyticsQuery, times(1))
                .aggregateByPeriod(any(), any(), anyBoolean(), any(), any(), any(), any(), any());

        // 2nd call served from Redis cache -> NO additional DB query
        ResponseEntity<JsonNode> second = get(uri, adultBearer);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().path("expense").asText()).isEqualTo("70000.0000");

        verify(analyticsQuery, times(1))
                .aggregateByPeriod(any(), any(), anyBoolean(), any(), any(), any(), any(), any());

        // Create a new expense via POST /api/v1/transactions -> triggers AnalyticsCacheInvalidator
        Map<String, Object> createBody = Map.of(
                "type", "EXPENSE",
                "accountId", adultArsAccountId.toString(),
                "categoryId", parentFoodId.toString(),
                "amount", "5000.00",
                "currencyCode", "ARS",
                "transactionDate", "2026-11-20",
                "description", "Panadería");
        ResponseEntity<JsonNode> created = client()
                .post()
                .uri("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBody)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 3rd call must miss invalidated cache, query DB again, and include the new 5000 expense
        ResponseEntity<JsonNode> third = get(uri, adultBearer);
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(third.getBody().path("expense").asText()).isEqualTo("75000.0000");

        verify(analyticsQuery, times(2))
                .aggregateByPeriod(any(), any(), anyBoolean(), any(), any(), any(), any(), any());
    }

    private void seedDataset() {
        familyId = fixtures.createFamily("Familia Analytics " + UUID.randomUUID());
        adultId = createNamedUser(
                familyId, "Ana Adulto", "#0F766E", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childFullId = createNamedUser(
                familyId, "Lauti Hijo", "#2563EB", Role.HIJO, FamilyVisibility.FAMILY_FULL);
        childSummaryId = createNamedUser(
                familyId, "Sofi Summary", "#7C3AED", Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        childOwnId = createNamedUser(
                familyId, "Tomi Own", "#DC2626", Role.HIJO, FamilyVisibility.OWN_ONLY);

        adultBearer = tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childFullBearer =
                tokens.bearer(childFullId, familyId, Role.HIJO, FamilyVisibility.FAMILY_FULL);
        childSummaryBearer =
                tokens.bearer(childSummaryId, familyId, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        childOwnBearer =
                tokens.bearer(childOwnId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        adultArsAccountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        adultUsdAccountId = fixtures.createAccount(familyId, adultId, "SAVINGS", "USD");
        childArsAccountId = fixtures.createAccount(familyId, childFullId, "WALLET", "ARS");

        parentFoodId = insertCategory(familyId, null, "Alimentación", "EXPENSE", "#10B981", "utensils");
        childSuperId = insertCategory(
                familyId, parentFoodId, "Supermercado", "EXPENSE", "#059669", "shopping-cart");
        parentTransportId = insertCategory(familyId, null, "Transporte", "EXPENSE", "#3B82F6", "car");

        // 3 months of IPC + USD_MEP
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 9, 1), new BigDecimal("100.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 10, 1), new BigDecimal("110.000000"));
        insertIndex("IPC_ACUMULADO", LocalDate.of(2026, 11, 1), new BigDecimal("121.000000"));
        insertIndex("USD_MEP", LocalDate.of(2026, 9, 1), new BigDecimal("1000.000000"));
        insertIndex("USD_MEP", LocalDate.of(2026, 10, 1), new BigDecimal("1000.000000"));
        insertIndex("USD_MEP", LocalDate.of(2026, 11, 1), new BigDecimal("1000.000000"));

        // Sep 2026 (IPC=100)
        insertTxWithSnapshot(
                adultId, adultId, adultArsAccountId, null, "INCOME", "POSTED",
                "100000.0000", "ARS", LocalDate.of(2026, 9, 5), "Sueldo Sep",
                "100000.0000", "100.0000", "100.000000");
        insertTxWithSnapshot(
                adultId, adultId, adultArsAccountId, childSuperId, "EXPENSE", "POSTED",
                "50000.0000", "ARS", LocalDate.of(2026, 9, 10), "Super Sep",
                "50000.0000", "50.0000", "100.000000");

        // Oct 2026 (IPC=110)
        insertTxWithSnapshot(
                adultId, adultId, adultArsAccountId, null, "INCOME", "POSTED",
                "120000.0000", "ARS", LocalDate.of(2026, 10, 5), "Sueldo Oct",
                "120000.0000", "120.0000", "110.000000");
        insertTxWithSnapshot(
                adultId, adultId, adultArsAccountId, childSuperId, "EXPENSE", "POSTED",
                "55000.0000", "ARS", LocalDate.of(2026, 10, 12), "Super Oct",
                "55000.0000", "55.0000", "110.000000");

        // Nov 2026 (IPC=121)
        insertTxWithSnapshot(
                adultId, adultId, adultArsAccountId, null, "INCOME", "POSTED",
                "200000.0000", "ARS", LocalDate.of(2026, 11, 1), "Sueldo Nov",
                "200000.0000", "200.0000", "121.000000");
        insertTxWithSnapshot(
                adultId, adultId, adultArsAccountId, childSuperId, "EXPENSE", "POSTED",
                "40000.0000", "ARS", LocalDate.of(2026, 11, 10), "Super Nov",
                "40000.0000", "40.0000", "121.000000");
        insertTxWithSnapshot(
                adultId, adultId, adultUsdAccountId, parentFoodId, "EXPENSE", "POSTED",
                "20.0000", "USD", LocalDate.of(2026, 11, 12), "Delivery USD",
                "20000.0000", "20.0000", "121.000000");
        insertTxWithSnapshot(
                adultId, adultId, adultArsAccountId, null, "EXPENSE", "POSTED",
                "10000.0000", "ARS", LocalDate.of(2026, 11, 15), "Varios sin categoría",
                "10000.0000", "10.0000", "121.000000");

        // Expense paid by adult, attributed to childFull
        insertTxWithSnapshot(
                adultId, childFullId, adultArsAccountId, parentTransportId, "EXPENSE", "POSTED",
                "15000.0000", "ARS", LocalDate.of(2026, 11, 18), "SUBE Lauti",
                "15000.0000", "15.0000", "121.000000");

        // Allowance pair (OUT never expense; IN only income in child PERSONAL)
        insertTxWithSnapshot(
                adultId, adultId, adultArsAccountId, null, "ALLOWANCE_OUT", "POSTED",
                "30000.0000", "ARS", LocalDate.of(2026, 11, 5), "Mensualidad OUT",
                "30000.0000", "30.0000", "121.000000");
        insertTxWithSnapshot(
                adultId, childFullId, childArsAccountId, null, "ALLOWANCE_IN", "POSTED",
                "30000.0000", "ARS", LocalDate.of(2026, 11, 5), "Mensualidad IN",
                "30000.0000", "30.0000", "121.000000");

        // Transfer pair (never counted)
        insertTxWithSnapshot(
                adultId, adultId, adultArsAccountId, null, "TRANSFER_OUT", "POSTED",
                "50000.0000", "ARS", LocalDate.of(2026, 11, 8), "Transferencia OUT",
                "50000.0000", "50.0000", "121.000000");
        insertTxWithSnapshot(
                adultId, adultId, adultArsAccountId, null, "TRANSFER_IN", "POSTED",
                "50000.0000", "ARS", LocalDate.of(2026, 11, 8), "Transferencia IN",
                "50000.0000", "50.0000", "121.000000");

        // Soft-deleted expense (never counted)
        UUID deletedTxId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id, family_id, user_id, attributed_to_user_id, account_id, category_id,
                    type, scope, status, amount, currency_code, transaction_date, description, deleted_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'EXPENSE', 'PERSONAL', 'POSTED', 99999.0000, 'ARS', '2026-11-11', 'Deleted', NOW())
                """,
                deletedTxId,
                familyId,
                adultId,
                adultId,
                adultArsAccountId,
                parentFoodId);
        insertSnapshot(deletedTxId, "99999.0000", "99.9990", "121.000000", LocalDate.of(2026, 11, 11));

        // PROJECTED installments in Dec 2026 (next calendar month after Nov 2026)
        UUID creditCardId = fixtures.createAccount(familyId, adultId, "CREDIT_CARD", "ARS");
        UUID planAdultId = insertInstallmentPlan(adultId, creditCardId, parentFoodId, "Heladera", "20000.0000");
        UUID planChildId = insertInstallmentPlan(childFullId, creditCardId, parentTransportId, "Bici", "10000.0000");
        insertProjectedInstallment(
                adultId, adultId, creditCardId, parentFoodId, planAdultId, 2, "20000.0000", LocalDate.of(2026, 12, 15));
        insertProjectedInstallment(
                adultId, childFullId, creditCardId, parentTransportId, planChildId, 2, "10000.0000", LocalDate.of(2026, 12, 20));
    }

    private UUID createNamedUser(
            UUID familyId, String fullName, String accentColor, Role role, FamilyVisibility vis) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id, family_id, email, password_hash, full_name, role, family_visibility,
                    accent_color, email_verified_at, two_factor_enabled, is_active
                ) VALUES (?, ?, ?, '$2a$10$testdummyhash000000000000000000000000000000000000000000',
                          ?, ?, ?, ?, NOW(), FALSE, TRUE)
                """,
                userId,
                familyId,
                "user-" + UUID.randomUUID() + "@familion.test",
                fullName,
                role.name(),
                vis.name(),
                accentColor);
        return userId;
    }

    private UUID insertCategory(
            UUID familyId, UUID parentId, String name, String kind, String colorHex, String icon) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO categories (id, family_id, parent_category_id, name, kind, color_hex, icon, is_system, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, TRUE)
                """,
                id,
                familyId,
                parentId,
                name,
                kind,
                colorHex,
                icon);
        return id;
    }

    private void insertIndex(String code, LocalDate date, BigDecimal value) {
        jdbcTemplate.update(
                """
                INSERT INTO economic_indices (id, index_code, effective_date, value, source)
                VALUES (?, ?, ?, ?, 'MANUAL')
                """,
                UUID.randomUUID(),
                code,
                date,
                value);
    }

    private UUID insertTxWithSnapshot(
            UUID userId,
            UUID attributedToUserId,
            UUID accountId,
            UUID categoryId,
            String type,
            String status,
            String amount,
            String currency,
            LocalDate date,
            String description,
            String baseAmount,
            String refAmount,
            String ipc) {
        UUID txId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id, family_id, user_id, attributed_to_user_id, account_id, category_id,
                    type, scope, status, amount, currency_code, transaction_date, description
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PERSONAL', ?, ?, ?, ?, ?)
                """,
                txId,
                familyId,
                userId,
                attributedToUserId,
                accountId,
                categoryId,
                type,
                status,
                new BigDecimal(amount),
                currency,
                date,
                description);
        insertSnapshot(txId, baseAmount, refAmount, ipc, date);
        return txId;
    }

    private void insertSnapshot(
            UUID txId, String baseAmount, String refAmount, String ipc, LocalDate date) {
        jdbcTemplate.update(
                """
                INSERT INTO economic_snapshots (
                    transaction_id, reference_currency, reference_rate_source, reference_exchange_rate,
                    amount_in_reference, base_currency_amount, ipc_index_value, snapshot_date
                ) VALUES (?, 'USD', 'USD_MEP', 1000.000000, ?, ?, ?, ?)
                """,
                txId,
                new BigDecimal(refAmount),
                new BigDecimal(baseAmount),
                ipc == null ? null : new BigDecimal(ipc),
                date);
    }

    private UUID insertInstallmentPlan(
            UUID userId, UUID creditCardAccountId, UUID categoryId, String description, String instAmount) {
        UUID planId = UUID.randomUUID();
        BigDecimal inst = new BigDecimal(instAmount);
        BigDecimal total = inst.multiply(BigDecimal.valueOf(3));
        jdbcTemplate.update(
                """
                INSERT INTO installment_plans (
                    id, family_id, user_id, credit_card_account_id, category_id, description,
                    currency_code, total_installments, has_interest, financed_total,
                    installment_amount, purchase_date, first_billing_date, status
                ) VALUES (?, ?, ?, ?, ?, ?, 'ARS', 3, FALSE, ?, ?, '2026-10-15', '2026-11-15', 'ACTIVE')
                """,
                planId,
                familyId,
                userId,
                creditCardAccountId,
                categoryId,
                description,
                total,
                inst);
        return planId;
    }

    private void insertProjectedInstallment(
            UUID userId,
            UUID attributedToUserId,
            UUID accountId,
            UUID categoryId,
            UUID planId,
            int installmentNumber,
            String amount,
            LocalDate date) {
        UUID txId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id, family_id, user_id, attributed_to_user_id, account_id, category_id,
                    type, scope, status, amount, currency_code, transaction_date,
                    installment_plan_id, installment_number
                ) VALUES (?, ?, ?, ?, ?, ?, 'EXPENSE', 'PERSONAL', 'PROJECTED', ?, 'ARS', ?, ?, ?)
                """,
                txId,
                familyId,
                userId,
                attributedToUserId,
                accountId,
                categoryId,
                new BigDecimal(amount),
                date,
                planId,
                installmentNumber);
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

    private void clearRedisKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private static JsonNode findDay(JsonNode days, String isoDate) {
        for (JsonNode day : days) {
            if (isoDate.equals(day.path("date").asText())) {
                return day;
            }
        }
        throw new AssertionError("Day not found in calendar response: " + isoDate);
    }

    private static boolean hasItem(JsonNode day, String kind, UUID refId) {
        for (JsonNode item : day.path("items")) {
            if (kind.equals(item.path("kind").asText())
                    && refId.toString().equals(item.path("refId").asText())) {
                return true;
            }
        }
        return false;
    }
}
