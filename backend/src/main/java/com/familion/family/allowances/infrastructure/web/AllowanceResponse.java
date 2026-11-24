package com.familion.family.allowances.infrastructure.web;

import com.familion.family.allowances.application.AllowanceView;
import com.familion.family.allowances.domain.AllowanceAdjustmentRule;
import com.familion.family.allowances.domain.AllowanceFrequency;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AllowanceResponse(
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
        @JsonProperty("isActive") boolean isActive,
        BigDecimal currentAmount,
        LocalDate nextDisbursementDate,
        LocalDate lastDisbursementDate,
        BigDecimal childBalance) {

    public record ChildSummary(UUID id, String fullName, String accentColor) {}

    public record AssignedBySummary(UUID id, String fullName) {}

    public record AccountSummary(UUID id, String name) {}

    public static AllowanceResponse from(AllowanceView view) {
        ChildSummary child = new ChildSummary(
                view.child().id(), view.child().fullName(), view.child().accentColor());
        AssignedBySummary assignedBy =
                new AssignedBySummary(view.assignedBy().id(), view.assignedBy().fullName());
        AccountSummary sourceAccount = view.sourceAccount() == null
                ? null
                : new AccountSummary(view.sourceAccount().id(), view.sourceAccount().name());
        AccountSummary targetAccount =
                new AccountSummary(view.targetAccount().id(), view.targetAccount().name());

        return new AllowanceResponse(
                view.id(),
                child,
                assignedBy,
                sourceAccount,
                targetAccount,
                view.amount(),
                view.currencyCode(),
                view.frequency(),
                view.dayOfPeriod(),
                view.adjustmentRule(),
                view.active(),
                view.currentAmount(),
                view.nextDisbursementDate(),
                view.lastDisbursementDate(),
                view.childBalance());
    }
}
