package com.familion.finance.goals.application;

import com.familion.finance.goals.domain.GoalScope;
import com.familion.finance.goals.domain.GoalStatus;
import com.familion.finance.goals.domain.TargetUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SavingsGoalView(
        UUID id,
        String name,
        GoalScope scope,
        UserSummary owner,
        BigDecimal targetAmount,
        TargetUnit targetUnit,
        LocalDate targetDate,
        GoalStatus status,
        BigDecimal accumulatedInUnit,
        BigDecimal progressPct,
        BigDecimal remainingInUnit,
        Integer monthsLeft,
        BigDecimal suggestedMonthlyInUnit,
        int contributionsCount,
        LocalDate lastContributionDate,
        BigDecimal accumulatedInBaseToday) {

    public record UserSummary(UUID id, String fullName) {}
}
