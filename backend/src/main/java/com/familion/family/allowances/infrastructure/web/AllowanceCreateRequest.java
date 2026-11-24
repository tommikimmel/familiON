package com.familion.family.allowances.infrastructure.web;

import com.familion.family.allowances.domain.AllowanceAdjustmentRule;
import com.familion.family.allowances.domain.AllowanceFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record AllowanceCreateRequest(
        @NotNull(message = "Child user is required") UUID childUserId,
        UUID sourceAccountId,
        @NotNull(message = "Target account is required") UUID targetAccountId,
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        BigDecimal amount,
        @NotBlank(message = "Currency code is required")
        @Size(max = 10, message = "Currency code must be at most 10 characters")
        String currencyCode,
        @NotNull(message = "Frequency is required") AllowanceFrequency frequency,
        @NotNull(message = "Day of period is required")
        @Min(value = 1, message = "Day of period must be between 1 and 31")
        @Max(value = 31, message = "Day of period must be between 1 and 31")
        Integer dayOfPeriod,
        @NotNull(message = "Adjustment rule is required")
        AllowanceAdjustmentRule adjustmentRule) {}
