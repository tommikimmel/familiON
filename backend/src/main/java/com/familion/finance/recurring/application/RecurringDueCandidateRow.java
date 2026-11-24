package com.familion.finance.recurring.application;

import com.familion.finance.recurring.domain.AdjustmentRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecurringDueCandidateRow(
        UUID id,
        UUID familyId,
        String name,
        BigDecimal baseAmount,
        String currencyCode,
        int dueDay,
        AdjustmentRule adjustmentRule,
        LocalDate startDate,
        LocalDate endDate) {}
