package com.familion.finance.goals.infrastructure.web;

import com.familion.finance.goals.application.ContributionView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContributionResponse(
        UUID id,
        UserSummary user,
        BigDecimal amount,
        String currencyCode,
        BigDecimal amountInGoalUnit,
        LocalDate contributionDate,
        String notes,
        UUID transactionId) {

    public record UserSummary(UUID id, String fullName) {}

    public static ContributionResponse from(ContributionView view) {
        return new ContributionResponse(
                view.id(),
                new UserSummary(view.user().id(), view.user().fullName()),
                view.amount(),
                view.currencyCode(),
                view.amountInGoalUnit(),
                view.contributionDate(),
                view.notes(),
                view.transactionId());
    }
}
