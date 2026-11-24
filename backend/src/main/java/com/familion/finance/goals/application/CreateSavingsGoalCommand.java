package com.familion.finance.goals.application;

import com.familion.finance.goals.domain.GoalScope;
import com.familion.finance.goals.domain.TargetUnit;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSavingsGoalCommand(
        String name,
        GoalScope scope,
        BigDecimal targetAmount,
        TargetUnit targetUnit,
        LocalDate targetDate) {}
