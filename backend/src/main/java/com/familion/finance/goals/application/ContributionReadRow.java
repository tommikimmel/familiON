package com.familion.finance.goals.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContributionReadRow(
        UUID id,
        UUID userId,
        String userFullName,
        BigDecimal amount,
        String currencyCode,
        BigDecimal amountInGoalUnit,
        LocalDate contributionDate,
        String notes,
        UUID transactionId) {}
