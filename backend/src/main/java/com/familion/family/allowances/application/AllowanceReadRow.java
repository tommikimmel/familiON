package com.familion.family.allowances.application;

import com.familion.family.allowances.domain.AllowanceAdjustmentRule;
import com.familion.family.allowances.domain.AllowanceFrequency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AllowanceReadRow(
        UUID id,
        UUID familyId,
        UUID childUserId,
        String childFullName,
        String childAccentColor,
        UUID assignedByUserId,
        String assignedByFullName,
        UUID sourceAccountId,
        String sourceAccountName,
        UUID targetAccountId,
        String targetAccountName,
        BigDecimal amount,
        String currencyCode,
        AllowanceFrequency frequency,
        int dayOfPeriod,
        AllowanceAdjustmentRule adjustmentRule,
        boolean active,
        Instant createdAt) {}
