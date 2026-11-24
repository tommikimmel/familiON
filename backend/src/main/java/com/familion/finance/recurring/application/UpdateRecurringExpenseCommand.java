package com.familion.finance.recurring.application;

import com.familion.finance.recurring.domain.AdjustmentRule;
import com.familion.finance.transactions.application.api.Scope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateRecurringExpenseCommand(
        String name,
        UUID categoryId,
        boolean accountIdPresent,
        UUID accountId,
        Scope scope,
        BigDecimal baseAmount,
        String currencyCode,
        Integer dueDay,
        AdjustmentRule adjustmentRule,
        LocalDate startDate,
        boolean endDatePresent,
        LocalDate endDate,
        Boolean active) {}
