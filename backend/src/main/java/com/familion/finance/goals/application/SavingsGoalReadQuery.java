package com.familion.finance.goals.application;

import com.familion.finance.goals.domain.GoalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavingsGoalReadQuery {

    List<SavingsGoalReadRow> findGoals(
            UUID familyId, UUID userId, GoalListingScope listingScope, GoalStatus statusFilter);

    Optional<SavingsGoalReadRow> findGoalById(UUID familyId, UUID goalId);

    List<ContributionReadRow> findContributionsByGoalId(UUID goalId);

    boolean transactionExistsInFamily(UUID familyId, UUID transactionId);
}
