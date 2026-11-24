package com.familion.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.domain.AnalyticsPeriod;
import com.familion.analytics.domain.CalendarItemKind;
import com.familion.analytics.domain.InvalidAnalyticsRequestException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.DefaultVisibilityPolicy;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.security.View;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final UUID FAMILY_ID = UUID.randomUUID();
    private static final UUID ADULT_ID = UUID.randomUUID();
    private static final UUID CHILD_ID = UUID.randomUUID();

    // 2026-11-24T01:30:00Z is 2026-11-23T22:30:00 in America/Argentina/Buenos_Aires
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-24T01:30:00Z"), ZoneOffset.UTC);

    private static final FamilySettings SETTINGS = new FamilySettings(
            FAMILY_ID,
            "Familia Test",
            "ARS",
            "USD",
            "USD_MEP",
            ZoneId.of("America/Argentina/Buenos_Aires"),
            List.of("ARS", "USD"));

    @Mock
    private AnalyticsQuery analyticsQuery;

    @Mock
    private AnalyticsCache analyticsCache;

    @Mock
    private FamilyQueryApi familyQueryApi;

    @Mock
    private EconomicsApi economicsApi;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        lenient().when(analyticsCache.get(any(), anyString(), any())).thenReturn(Optional.empty());
        service = new AnalyticsService(
                analyticsQuery,
                analyticsCache,
                new DefaultVisibilityPolicy(),
                familyQueryApi,
                economicsApi,
                FIXED_CLOCK);
    }

    @Test
    void summaryUsesFamilyTimezoneForDefaultDateAndCalculatesMetrics() {
        CurrentUser adult = new CurrentUser(ADULT_ID, FAMILY_ID, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        when(familyQueryApi.getSettings(FAMILY_ID)).thenReturn(SETTINGS);
        when(economicsApi.latestValue("IPC_ACUMULADO")).thenReturn(Optional.of(new BigDecimal("120.000000")));

        List<PeriodAggregateRow> rows = List.of(
                new PeriodAggregateRow(
                        LocalDate.of(2026, 9, 1),
                        new BigDecimal("100000.0000"),
                        new BigDecimal("40000.0000"),
                        new BigDecimal("100000.0000"),
                        new BigDecimal("40000.0000"),
                        new BigDecimal("48000.0000"),
                        false),
                new PeriodAggregateRow(
                        LocalDate.of(2026, 10, 1),
                        new BigDecimal("120000.0000"),
                        new BigDecimal("50000.0000"),
                        new BigDecimal("120000.0000"),
                        new BigDecimal("50000.0000"),
                        new BigDecimal("52000.0000"),
                        false),
                new PeriodAggregateRow(
                        LocalDate.of(2026, 11, 1),
                        new BigDecimal("200000.0000"),
                        new BigDecimal("60000.0000"),
                        new BigDecimal("200000.0000"),
                        new BigDecimal("60000.0000"),
                        new BigDecimal("60000.0000"),
                        false));

        when(analyticsQuery.aggregateByPeriod(
                        eq(FAMILY_ID),
                        eq(ADULT_ID),
                        eq(true),
                        eq(AnalyticsGranularity.MONTH),
                        eq(AnalyticsLens.NOMINAL),
                        eq(new BigDecimal("120.000000")),
                        eq(LocalDate.of(2026, 8, 1)),
                        eq(LocalDate.of(2026, 11, 30))))
                .thenReturn(rows);
        when(analyticsQuery.sumProjectedInstallments(
                        FAMILY_ID, ADULT_ID, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(new BigDecimal("30000.0000"));

        AnalyticsSummary summary = service.getSummary(
                adult, View.PERSONAL, AnalyticsLens.NOMINAL, AnalyticsGranularity.MONTH, null);

        assertThat(summary.period().label()).isEqualTo("Noviembre 2026");
        assertThat(summary.period().from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(summary.unit()).isEqualTo("ARS");
        assertThat(summary.income()).isEqualByComparingTo("200000.0000");
        assertThat(summary.expense()).isEqualByComparingTo("60000.0000");
        assertThat(summary.balance()).isEqualByComparingTo("140000.0000");
        assertThat(summary.savingsRatePct()).isEqualByComparingTo("70.0000");
        assertThat(summary.installmentCommitmentPct()).isEqualByComparingTo("15.0000");
        assertThat(summary.previous().income()).isEqualByComparingTo("120000.0000");
        assertThat(summary.previous().expense()).isEqualByComparingTo("50000.0000");
        assertThat(summary.realVariationPct()).isEqualByComparingTo("20.0000");
        assertThat(summary.lensApproximated()).isFalse();
        verify(analyticsCache).put(eq(FAMILY_ID), anyString(), eq(summary));
    }

    @Test
    void summaryReturnsCachedResultWithoutQueryingDatabase() {
        CurrentUser adult = new CurrentUser(ADULT_ID, FAMILY_ID, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        AnalyticsSummary cached = new AnalyticsSummary(
                new AnalyticsPeriod(
                        AnalyticsGranularity.MONTH,
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 30),
                        "Noviembre 2026"),
                View.PERSONAL,
                AnalyticsLens.NOMINAL,
                "ARS",
                BigDecimal.TEN,
                BigDecimal.ONE,
                new BigDecimal("9"),
                new BigDecimal("90"),
                BigDecimal.ZERO,
                new AnalyticsSummary.PreviousPeriodTotals(BigDecimal.ZERO, BigDecimal.ZERO),
                null,
                false);
        when(analyticsCache.get(eq(FAMILY_ID), anyString(), eq(AnalyticsSummary.class)))
                .thenReturn(Optional.of(cached));

        AnalyticsSummary result = service.getSummary(
                adult,
                View.PERSONAL,
                AnalyticsLens.NOMINAL,
                AnalyticsGranularity.MONTH,
                LocalDate.of(2026, 11, 23));

        assertThat(result).isSameAs(cached);
        verify(analyticsQuery, never())
                .aggregateByPeriod(any(), any(), anyBoolean(), any(), any(), any(), any(), any());
    }

    @Test
    void summaryInIncomePctReturnsNullsWhenIncomeIsZero() {
        CurrentUser adult = new CurrentUser(ADULT_ID, FAMILY_ID, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        when(familyQueryApi.getSettings(FAMILY_ID)).thenReturn(SETTINGS);
        when(economicsApi.latestValue("IPC_ACUMULADO")).thenReturn(Optional.empty());
        when(analyticsQuery.aggregateByPeriod(
                        eq(FAMILY_ID),
                        isNull(),
                        eq(false),
                        eq(AnalyticsGranularity.DAY),
                        eq(AnalyticsLens.INCOME_PCT),
                        isNull(),
                        any(),
                        any()))
                .thenReturn(List.of(new PeriodAggregateRow(
                        LocalDate.of(2026, 11, 23),
                        BigDecimal.ZERO,
                        new BigDecimal("5000.0000"),
                        BigDecimal.ZERO,
                        new BigDecimal("5000.0000"),
                        new BigDecimal("5000.0000"),
                        false)));
        when(analyticsQuery.sumProjectedInstallments(eq(FAMILY_ID), isNull(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        AnalyticsSummary summary = service.getSummary(
                adult,
                View.FAMILY,
                AnalyticsLens.INCOME_PCT,
                AnalyticsGranularity.DAY,
                LocalDate.of(2026, 11, 23));

        assertThat(summary.unit()).isEqualTo("%");
        assertThat(summary.income()).isNull();
        assertThat(summary.expense()).isNull();
        assertThat(summary.balance()).isNull();
        assertThat(summary.savingsRatePct()).isNull();
        assertThat(summary.installmentCommitmentPct()).isNull();
        assertThat(summary.previous().income()).isNull();
        assertThat(summary.previous().expense()).isNull();
    }

    @Test
    void summaryRejectsOwnOnlyChildRequestingFamilyView() {
        CurrentUser child = new CurrentUser(CHILD_ID, FAMILY_ID, Role.HIJO, FamilyVisibility.OWN_ONLY);

        assertThatThrownBy(() -> service.getSummary(
                        child, View.FAMILY, AnalyticsLens.NOMINAL, AnalyticsGranularity.MONTH, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void seriesFillsMissingPeriodsWithZeroAndValidatesBounds() {
        CurrentUser childSummary =
                new CurrentUser(CHILD_ID, FAMILY_ID, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        when(familyQueryApi.getSettings(FAMILY_ID)).thenReturn(SETTINGS);
        when(analyticsQuery.aggregateByPeriod(
                        eq(FAMILY_ID),
                        isNull(),
                        eq(false),
                        eq(AnalyticsGranularity.MONTH),
                        eq(AnalyticsLens.NOMINAL),
                        isNull(),
                        eq(LocalDate.of(2026, 9, 1)),
                        eq(LocalDate.of(2026, 11, 30))))
                .thenReturn(List.of(new PeriodAggregateRow(
                        LocalDate.of(2026, 10, 1),
                        new BigDecimal("50000.0000"),
                        new BigDecimal("20000.0000"),
                        new BigDecimal("50000.0000"),
                        new BigDecimal("20000.0000"),
                        new BigDecimal("20000.0000"),
                        false)));

        AnalyticsSeries series = service.getSeries(
                childSummary,
                View.FAMILY,
                AnalyticsLens.NOMINAL,
                AnalyticsGranularity.MONTH,
                LocalDate.of(2026, 11, 23),
                3);

        assertThat(series.points()).hasSize(3);
        assertThat(series.points().get(0).periodStart()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(series.points().get(0).income()).isEqualByComparingTo("0.0000");
        assertThat(series.points().get(1).periodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(series.points().get(1).balance()).isEqualByComparingTo("30000.0000");
        assertThat(series.points().get(2).periodStart()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(series.points().get(2).expense()).isEqualByComparingTo("0.0000");

        assertThatThrownBy(() -> service.getSeries(
                        childSummary,
                        View.PERSONAL,
                        AnalyticsLens.NOMINAL,
                        AnalyticsGranularity.MONTH,
                        LocalDate.of(2026, 11, 23),
                        0))
                .isInstanceOf(InvalidAnalyticsRequestException.class);
        assertThatThrownBy(() -> service.getSeries(
                        childSummary,
                        View.PERSONAL,
                        AnalyticsLens.NOMINAL,
                        AnalyticsGranularity.MONTH,
                        LocalDate.of(2026, 11, 23),
                        25))
                .isInstanceOf(InvalidAnalyticsRequestException.class);
    }

    @Test
    void seriesInIncomePctReturns100IncomeAndExpenseRatioOrNulls() {
        CurrentUser adult = new CurrentUser(ADULT_ID, FAMILY_ID, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        when(familyQueryApi.getSettings(FAMILY_ID)).thenReturn(SETTINGS);
        when(analyticsQuery.aggregateByPeriod(
                        eq(FAMILY_ID),
                        eq(ADULT_ID),
                        anyBoolean(),
                        eq(AnalyticsGranularity.MONTH),
                        eq(AnalyticsLens.INCOME_PCT),
                        isNull(),
                        any(),
                        any()))
                .thenReturn(List.of(new PeriodAggregateRow(
                        LocalDate.of(2026, 11, 1),
                        new BigDecimal("100000.0000"),
                        new BigDecimal("25000.0000"),
                        new BigDecimal("100000.0000"),
                        new BigDecimal("25000.0000"),
                        new BigDecimal("25000.0000"),
                        false)));

        AnalyticsSeries series = service.getSeries(
                adult,
                View.PERSONAL,
                AnalyticsLens.INCOME_PCT,
                AnalyticsGranularity.MONTH,
                LocalDate.of(2026, 11, 23),
                2);

        assertThat(series.unit()).isEqualTo("%");
        assertThat(series.points().get(0).income()).isNull();
        assertThat(series.points().get(0).expense()).isNull();
        assertThat(series.points().get(0).balance()).isNull();
        assertThat(series.points().get(1).income()).isEqualByComparingTo("100.0000");
        assertThat(series.points().get(1).expense()).isEqualByComparingTo("25.0000");
        assertThat(series.points().get(1).balance()).isEqualByComparingTo("75.0000");
    }

    @Test
    void byCategoryMapsUncategorizedAndComputesVariationInConstantBase() {
        CurrentUser adult = new CurrentUser(ADULT_ID, FAMILY_ID, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        UUID foodId = UUID.randomUUID();
        when(familyQueryApi.getSettings(FAMILY_ID)).thenReturn(SETTINGS);
        when(economicsApi.latestValue("IPC_ACUMULADO")).thenReturn(Optional.of(new BigDecimal("120.000000")));
        when(analyticsQuery.aggregateByCategory(
                        FAMILY_ID,
                        ADULT_ID,
                        AnalyticsLens.NOMINAL,
                        new BigDecimal("120.000000"),
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 30)))
                .thenReturn(List.of(
                        new CategoryAggregateRow(
                                foodId,
                                "Alimentación",
                                "#10B981",
                                "utensils",
                                new BigDecimal("75000.0000"),
                                new BigDecimal("75000.0000"),
                                new BigDecimal("75000.0000")),
                        new CategoryAggregateRow(
                                null,
                                null,
                                null,
                                null,
                                new BigDecimal("25000.0000"),
                                new BigDecimal("25000.0000"),
                                new BigDecimal("25000.0000"))));

        Map<UUID, BigDecimal> prevMap = new HashMap<>();
        prevMap.put(foodId, new BigDecimal("50000.0000"));
        when(analyticsQuery.aggregateCategoryConstantBase(
                        FAMILY_ID,
                        ADULT_ID,
                        new BigDecimal("120.000000"),
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 31)))
                .thenReturn(prevMap);

        CategoryBreakdown breakdown = service.getByCategory(
                adult,
                View.PERSONAL,
                AnalyticsLens.NOMINAL,
                AnalyticsGranularity.MONTH,
                LocalDate.of(2026, 11, 23));

        assertThat(breakdown.total()).isEqualByComparingTo("100000.0000");
        assertThat(breakdown.slices()).hasSize(2);
        assertThat(breakdown.slices().get(0).categoryId()).isEqualTo(foodId);
        assertThat(breakdown.slices().get(0).pct()).isEqualByComparingTo("75.0000");
        assertThat(breakdown.slices().get(0).variationPct()).isEqualByComparingTo("50.0000");
        assertThat(breakdown.slices().get(1).categoryId()).isNull();
        assertThat(breakdown.slices().get(1).name()).isEqualTo("Sin categoría");
        assertThat(breakdown.slices().get(1).pct()).isEqualByComparingTo("25.0000");
        assertThat(breakdown.slices().get(1).variationPct()).isNull();
    }

    @Test
    void byPersonRejectsPersonalViewAndFamilySummaryVisibility() {
        CurrentUser adult = new CurrentUser(ADULT_ID, FAMILY_ID, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        CurrentUser childSummary =
                new CurrentUser(CHILD_ID, FAMILY_ID, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        CurrentUser childOwn = new CurrentUser(CHILD_ID, FAMILY_ID, Role.HIJO, FamilyVisibility.OWN_ONLY);

        assertThatThrownBy(() -> service.getByPerson(
                        adult,
                        View.PERSONAL,
                        AnalyticsLens.NOMINAL,
                        AnalyticsGranularity.MONTH,
                        LocalDate.of(2026, 11, 23)))
                .isInstanceOf(InvalidAnalyticsRequestException.class);

        assertThatThrownBy(() -> service.getByPerson(
                        childSummary,
                        View.FAMILY,
                        AnalyticsLens.NOMINAL,
                        AnalyticsGranularity.MONTH,
                        LocalDate.of(2026, 11, 23)))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> service.getByPerson(
                        childOwn,
                        View.FAMILY,
                        AnalyticsLens.NOMINAL,
                        AnalyticsGranularity.MONTH,
                        LocalDate.of(2026, 11, 23)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void byPersonComputesSlicesAndPercentagesForFamilyFull() {
        CurrentUser adult = new CurrentUser(ADULT_ID, FAMILY_ID, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        when(familyQueryApi.getSettings(FAMILY_ID)).thenReturn(SETTINGS);
        when(analyticsQuery.aggregateByPerson(
                        FAMILY_ID,
                        AnalyticsLens.NOMINAL,
                        null,
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 30)))
                .thenReturn(List.of(
                        new PersonAggregateRow(
                                ADULT_ID,
                                "Ana Adulto",
                                "#0F766E",
                                new BigDecimal("60000.0000"),
                                new BigDecimal("60000.0000")),
                        new PersonAggregateRow(
                                CHILD_ID,
                                "Lauti Hijo",
                                "#2563EB",
                                new BigDecimal("40000.0000"),
                                new BigDecimal("40000.0000"))));

        PersonBreakdown breakdown = service.getByPerson(
                adult,
                View.FAMILY,
                AnalyticsLens.NOMINAL,
                AnalyticsGranularity.MONTH,
                LocalDate.of(2026, 11, 23));

        assertThat(breakdown.total()).isEqualByComparingTo("100000.0000");
        assertThat(breakdown.slices()).hasSize(2);
        assertThat(breakdown.slices().get(0).pct()).isEqualByComparingTo("60.0000");
        assertThat(breakdown.slices().get(1).pct()).isEqualByComparingTo("40.0000");
    }

    @Test
    void calendarValidatesRangeAndClampsRecurringDueToMonthEndWhileOmittingPaid() {
        CurrentUser adult = new CurrentUser(ADULT_ID, FAMILY_ID, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        // 63 inclusive days -> rejected
        assertThatThrownBy(() -> service.getCalendar(
                        adult,
                        View.FAMILY,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 3, 4)))
                .isInstanceOf(InvalidAnalyticsRequestException.class);

        // to < from -> rejected
        assertThatThrownBy(() -> service.getCalendar(
                        adult,
                        View.FAMILY,
                        LocalDate.of(2026, 2, 10),
                        LocalDate.of(2026, 2, 1)))
                .isInstanceOf(InvalidAnalyticsRequestException.class);

        UUID unpaidRecId = UUID.randomUUID();
        UUID paidRecId = UUID.randomUUID();
        UUID allowanceId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 2, 25);
        LocalDate to = LocalDate.of(2026, 2, 28);

        when(analyticsQuery.findCalendarDailyTotals(FAMILY_ID, null, false, from, to))
                .thenReturn(List.of());
        when(analyticsQuery.findCalendarTransactionItems(FAMILY_ID, null, from, to))
                .thenReturn(List.of());
        when(analyticsQuery.findActiveRecurringExpenses(FAMILY_ID, null, from, to))
                .thenReturn(List.of(
                        new CalendarRows.RecurringExpenseRow(
                                unpaidRecId,
                                "Alquiler",
                                new BigDecimal("250000.0000"),
                                "ARS",
                                31,
                                LocalDate.of(2026, 1, 1),
                                null),
                        new CalendarRows.RecurringExpenseRow(
                                paidRecId,
                                "Internet",
                                new BigDecimal("25000.0000"),
                                "ARS",
                                28,
                                LocalDate.of(2026, 1, 1),
                                null)));
        when(analyticsQuery.findPaidRecurringKeys(
                        FAMILY_ID, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .thenReturn(Set.of(new CalendarRows.PaidRecurringKey(paidRecId, YearMonth.of(2026, 2))));
        when(analyticsQuery.findVehicleDues(FAMILY_ID, from, to)).thenReturn(List.of());
        when(analyticsQuery.findActiveAllowances(FAMILY_ID, null))
                .thenReturn(List.of(new CalendarRows.AllowanceRow(
                        allowanceId,
                        "Lauti",
                        new BigDecimal("15000.0000"),
                        "ARS",
                        "WEEKLY",
                        5))); // Friday 2026-02-27

        CalendarView calendar = service.getCalendar(adult, View.FAMILY, from, to);

        assertThat(calendar.days()).hasSize(4);
        // 2026-02-27 is Friday -> weekly allowance
        CalendarView.CalendarDay feb27 = calendar.days().get(2);
        assertThat(feb27.date()).isEqualTo(LocalDate.of(2026, 2, 27));
        assertThat(feb27.items())
                .extracting(CalendarView.CalendarItem::kind, CalendarView.CalendarItem::refId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(CalendarItemKind.ALLOWANCE, allowanceId));

        // 2026-02-28 -> unpaid recurring (due_day=31 clamped to 28); paid recurring omitted
        CalendarView.CalendarDay feb28 = calendar.days().get(3);
        assertThat(feb28.date()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(feb28.items())
                .extracting(CalendarView.CalendarItem::kind, CalendarView.CalendarItem::refId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(CalendarItemKind.RECURRING_DUE, unpaidRecId));
    }

    @Test
    void calendarPersonalViewSkipsVehicleDues() {
        CurrentUser adult = new CurrentUser(ADULT_ID, FAMILY_ID, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        LocalDate date = LocalDate.of(2026, 11, 23);

        when(analyticsQuery.findCalendarDailyTotals(FAMILY_ID, ADULT_ID, true, date, date))
                .thenReturn(List.of());
        when(analyticsQuery.findCalendarTransactionItems(FAMILY_ID, ADULT_ID, date, date))
                .thenReturn(List.of());
        when(analyticsQuery.findActiveRecurringExpenses(FAMILY_ID, ADULT_ID, date, date))
                .thenReturn(List.of());
        when(analyticsQuery.findActiveAllowances(FAMILY_ID, ADULT_ID)).thenReturn(List.of());

        CalendarView view = service.getCalendar(adult, View.PERSONAL, date, date);

        assertThat(view.days()).hasSize(1);
        verify(analyticsQuery, never()).findVehicleDues(any(), any(), any());
    }
}
