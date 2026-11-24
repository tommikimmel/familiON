package com.familion.finance.goals.infrastructure.web;

import com.familion.finance.goals.domain.GoalStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public record SavingsGoalPatchRequest(
        @Pattern(regexp = ".*\\S.*", message = "Savings goal name must not be blank")
        @Size(max = 120, message = "Savings goal name must be at most 120 characters")
        String name,
        @Positive(message = "Target amount must be greater than zero") BigDecimal targetAmount,
        Optional<LocalDate> targetDate,
        GoalStatus status) {}
