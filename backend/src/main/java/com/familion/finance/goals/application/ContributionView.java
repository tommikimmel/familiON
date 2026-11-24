package com.familion.finance.goals.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContributionView(
        UUID id,
        UserSummary user,
        BigDecimal amount,
        String currencyCode,
        BigDecimal amountInGoalUnit,
        LocalDate contributionDate,
        String notes,
        UUID transactionId) {

    public record UserSummary(UUID id, String fullName) {}
}
