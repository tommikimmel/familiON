package com.familion.finance.goals.application;

import com.familion.finance.goals.domain.GoalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PatchSavingsGoalCommand(
        String name,
        BigDecimal targetAmount,
        boolean targetDatePresent,
        LocalDate targetDate,
        GoalStatus status) {}
