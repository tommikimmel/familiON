package com.familion.analytics.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public final class CalendarRows {

    private CalendarRows() {}

    public record DailyTotalRow(LocalDate date, BigDecimal income, BigDecimal expense) {}

    public record TransactionItemRow(
            UUID id,
            LocalDate date,
            String description,
            String categoryName,
            BigDecimal amount,
            String currencyCode,
            String status,
            UUID installmentPlanId,
            Integer installmentNumber,
            Integer totalInstallments,
            String planDescription) {}

    public record RecurringExpenseRow(
            UUID id,
            String name,
            BigDecimal baseAmount,
            String currencyCode,
            int dueDay,
            LocalDate startDate,
            LocalDate endDate) {}

    public record PaidRecurringKey(UUID recurringExpenseId, YearMonth yearMonth) {}

    public record VehicleDueRow(UUID refId, LocalDate dueDate, String title) {}

    public record AllowanceRow(
            UUID id,
            String childFullName,
            BigDecimal amount,
            String currencyCode,
            String frequency,
            int dayOfPeriod) {}
}
