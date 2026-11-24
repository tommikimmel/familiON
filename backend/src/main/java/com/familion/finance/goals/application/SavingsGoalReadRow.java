package com.familion.finance.goals.application;

import com.familion.finance.goals.domain.GoalScope;
import com.familion.finance.goals.domain.GoalStatus;
import com.familion.finance.goals.domain.TargetUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SavingsGoalReadRow(
        UUID id,
        String name,
        GoalScope scope,
        UUID ownerUserId,
        String ownerFullName,
        BigDecimal targetAmount,
        TargetUnit targetUnit,
        LocalDate targetDate,
        GoalStatus status,
        Instant createdAt,
        BigDecimal accumulatedInUnit,
        int contributionsCount,
        LocalDate lastContributionDate) {}
