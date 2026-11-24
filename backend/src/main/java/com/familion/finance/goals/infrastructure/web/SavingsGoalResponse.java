package com.familion.finance.goals.infrastructure.web;

import com.familion.finance.goals.application.SavingsGoalView;
import com.familion.finance.goals.domain.GoalScope;
import com.familion.finance.goals.domain.GoalStatus;
import com.familion.finance.goals.domain.TargetUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SavingsGoalResponse(
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

    public static SavingsGoalResponse from(SavingsGoalView view) {
        UserSummary owner = view.owner() == null
                ? null
                : new UserSummary(view.owner().id(), view.owner().fullName());
        return new SavingsGoalResponse(
                view.id(),
                view.name(),
                view.scope(),
                owner,
                view.targetAmount(),
                view.targetUnit(),
                view.targetDate(),
                view.status(),
                view.accumulatedInUnit(),
                view.progressPct(),
                view.remainingInUnit(),
                view.monthsLeft(),
                view.suggestedMonthlyInUnit(),
                view.contributionsCount(),
                view.lastContributionDate(),
                view.accumulatedInBaseToday());
    }
}
