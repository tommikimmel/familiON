package com.familion.finance.recurring.application;

import com.familion.finance.recurring.domain.AdjustmentRule;
import com.familion.finance.recurring.domain.RecurringMonthStatus;
import com.familion.finance.transactions.application.api.Scope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public record RecurringExpenseView(
        UUID id,
        String name,
        CategorySummary category,
        AccountSummary account,
        Scope scope,
        BigDecimal baseAmount,
        String currencyCode,
        int dueDay,
        AdjustmentRule adjustmentRule,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        MonthStatusView month) {

    public record CategorySummary(UUID id, String name, String icon, String colorHex) {}

    public record AccountSummary(UUID id, String name) {}

    public record MonthStatusView(
            YearMonth month,
            LocalDate dueDate,
            BigDecimal estimatedAmount,
            RecurringMonthStatus status,
            UUID paidTransactionId,
            BigDecimal paidAmount,
            LocalDate paidDate) {}
}
