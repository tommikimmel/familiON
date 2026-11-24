package com.familion.finance.recurring.application;

import com.familion.finance.recurring.domain.AdjustmentRule;
import com.familion.finance.transactions.application.api.Scope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringReadRow(
        UUID id,
        UUID familyId,
        UUID createdByUserId,
        String name,
        UUID categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColorHex,
        UUID accountId,
        String accountName,
        Scope scope,
        BigDecimal baseAmount,
        String currencyCode,
        int dueDay,
        AdjustmentRule adjustmentRule,
        LocalDate startDate,
        LocalDate endDate,
        boolean active) {}
