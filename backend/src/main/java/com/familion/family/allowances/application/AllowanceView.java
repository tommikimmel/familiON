package com.familion.family.allowances.application;

import com.familion.family.allowances.domain.AllowanceAdjustmentRule;
import com.familion.family.allowances.domain.AllowanceFrequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AllowanceView(
        UUID id,
        ChildSummary child,
        AssignedBySummary assignedBy,
        AccountSummary sourceAccount,
        AccountSummary targetAccount,
        BigDecimal amount,
        String currencyCode,
        AllowanceFrequency frequency,
        int dayOfPeriod,
        AllowanceAdjustmentRule adjustmentRule,
        boolean active,
        BigDecimal currentAmount,
        LocalDate nextDisbursementDate,
        LocalDate lastDisbursementDate,
        BigDecimal childBalance) {

    public record ChildSummary(UUID id, String fullName, String accentColor) {}

    public record AssignedBySummary(UUID id, String fullName) {}

    public record AccountSummary(UUID id, String name) {}
}
