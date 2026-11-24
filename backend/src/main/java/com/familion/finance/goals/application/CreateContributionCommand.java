package com.familion.finance.goals.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateContributionCommand(
        BigDecimal amount,
        String currencyCode,
        LocalDate contributionDate,
        String notes,
        UUID transactionId) {}
