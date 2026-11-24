package com.familion.finance.recurring.application.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringDue(
        UUID recurringExpenseId,
        UUID familyId,
        String name,
        LocalDate dueDate,
        BigDecimal estimatedAmount,
        String currencyCode) {}
