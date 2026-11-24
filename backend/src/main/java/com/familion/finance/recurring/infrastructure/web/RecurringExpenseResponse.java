package com.familion.finance.recurring.infrastructure.web;

import com.familion.finance.recurring.application.RecurringExpenseView;
import com.familion.finance.recurring.domain.AdjustmentRule;
import com.familion.finance.recurring.domain.RecurringMonthStatus;
import com.familion.finance.transactions.application.api.Scope;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public record RecurringExpenseResponse(
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
        @JsonProperty("isActive") boolean isActive,
        MonthStatusResponse month) {

    public record CategorySummary(UUID id, String name, String icon, String colorHex) {}

    public record AccountSummary(UUID id, String name) {}

    public record MonthStatusResponse(
            YearMonth month,
            LocalDate dueDate,
            BigDecimal estimatedAmount,
            RecurringMonthStatus status,
            UUID paidTransactionId,
            BigDecimal paidAmount,
            LocalDate paidDate) {}

    public static RecurringExpenseResponse from(RecurringExpenseView view) {
        CategorySummary category = new CategorySummary(
                view.category().id(),
                view.category().name(),
                view.category().icon(),
                view.category().colorHex());
        AccountSummary account = view.account() == null
                ? null
                : new AccountSummary(view.account().id(), view.account().name());
        MonthStatusResponse month = new MonthStatusResponse(
                view.month().month(),
                view.month().dueDate(),
                view.month().estimatedAmount(),
                view.month().status(),
                view.month().paidTransactionId(),
                view.month().paidAmount(),
                view.month().paidDate());

        return new RecurringExpenseResponse(
                view.id(),
                view.name(),
                category,
                account,
                view.scope(),
                view.baseAmount(),
                view.currencyCode(),
                view.dueDay(),
                view.adjustmentRule(),
                view.startDate(),
                view.endDate(),
                view.active(),
                month);
    }
}
