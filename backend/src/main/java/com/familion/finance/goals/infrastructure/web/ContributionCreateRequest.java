package com.familion.finance.goals.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContributionCreateRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        BigDecimal amount,
        @NotBlank(message = "Currency code is required")
        @Size(max = 10, message = "Currency code must be at most 10 characters")
        String currencyCode,
        LocalDate contributionDate,
        @Size(max = 255, message = "Notes must be at most 255 characters") String notes,
        UUID transactionId) {}
