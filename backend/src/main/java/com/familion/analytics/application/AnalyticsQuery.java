package com.familion.analytics.application;

import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface AnalyticsQuery {

    List<PeriodAggregateRow> aggregateByPeriod(
            UUID familyId,
            UUID viewerUserId,
            boolean includeAllowanceIn,
            AnalyticsGranularity granularity,
            AnalyticsLens lens,
            BigDecimal latestIpc,
            LocalDate from,
            LocalDate to);

    BigDecimal sumProjectedInstallments(
            UUID familyId, UUID viewerUserId, LocalDate from, LocalDate to);

    BigDecimal sumNominalIncome(
            UUID familyId,
            UUID viewerUserId,
            boolean includeAllowanceIn,
            LocalDate from,
            LocalDate to);

    List<CategoryAggregateRow> aggregateByCategory(
            UUID familyId,
            UUID viewerUserId,
            AnalyticsLens lens,
            BigDecimal latestIpc,
            LocalDate from,
            LocalDate to);

    Map<UUID, BigDecimal> aggregateCategoryConstantBase(
            UUID familyId, UUID viewerUserId, BigDecimal latestIpc, LocalDate from, LocalDate to);

    List<PersonAggregateRow> aggregateByPerson(
            UUID familyId,
            AnalyticsLens lens,
            BigDecimal latestIpc,
            LocalDate from,
            LocalDate to);

    List<CalendarRows.DailyTotalRow> findCalendarDailyTotals(
            UUID familyId,
            UUID viewerUserId,
            boolean includeAllowanceIn,
            LocalDate from,
            LocalDate to);

    List<CalendarRows.TransactionItemRow> findCalendarTransactionItems(
            UUID familyId, UUID viewerUserId, LocalDate from, LocalDate to);

    List<CalendarRows.RecurringExpenseRow> findActiveRecurringExpenses(
            UUID familyId, UUID viewerUserId, LocalDate from, LocalDate to);

    Set<CalendarRows.PaidRecurringKey> findPaidRecurringKeys(
            UUID familyId, LocalDate firstMonthStart, LocalDate lastMonthEnd);

    List<CalendarRows.VehicleDueRow> findVehicleDues(
            UUID familyId, LocalDate from, LocalDate to);

    List<CalendarRows.AllowanceRow> findActiveAllowances(UUID familyId, UUID viewerUserId);
}
