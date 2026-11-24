package com.familion.family.allowances.application;

import com.familion.family.allowances.domain.AllowanceAdjustmentRule;
import com.familion.family.allowances.domain.AllowanceFrequency;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateAllowanceCommand(
        UUID childUserId,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        String currencyCode,
        AllowanceFrequency frequency,
        Integer dayOfPeriod,
        AllowanceAdjustmentRule adjustmentRule) {}
