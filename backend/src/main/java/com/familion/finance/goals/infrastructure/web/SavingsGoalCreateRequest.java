package com.familion.finance.goals.infrastructure.web;

import com.familion.finance.goals.domain.GoalScope;
import com.familion.finance.goals.domain.TargetUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingsGoalCreateRequest(
        @NotBlank(message = "Savings goal name is required")
        @Size(max = 120, message = "Savings goal name must be at most 120 characters")
        String name,
        @NotNull(message = "Scope is required") GoalScope scope,
        @NotNull(message = "Target amount is required")
        @Positive(message = "Target amount must be greater than zero")
        BigDecimal targetAmount,
        @NotNull(message = "Target unit is required") TargetUnit targetUnit,
        LocalDate targetDate) {}
