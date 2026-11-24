package com.familion.finance.recurring.infrastructure.web;

import com.familion.finance.recurring.domain.AdjustmentRule;
import com.familion.finance.transactions.application.api.Scope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringExpenseCreateRequest(
        @NotBlank(message = "Recurring expense name is required")
        @Size(max = 120, message = "Recurring expense name must be at most 120 characters")
        String name,
        @NotNull(message = "Category is required") UUID categoryId,
        UUID accountId,
        Scope scope,
        @NotNull(message = "Base amount is required")
        @Positive(message = "Base amount must be greater than zero")
        BigDecimal baseAmount,
        @NotBlank(message = "Currency code is required")
        @Size(max = 10, message = "Currency code must be at most 10 characters")
        String currencyCode,
        @NotNull(message = "Due day is required")
        @Min(value = 1, message = "Due day must be between 1 and 31")
        @Max(value = 31, message = "Due day must be between 1 and 31")
        Integer dueDay,
        @NotNull(message = "Adjustment rule is required") AdjustmentRule adjustmentRule,
        @NotNull(message = "Start date is required") LocalDate startDate,
        LocalDate endDate) {}
