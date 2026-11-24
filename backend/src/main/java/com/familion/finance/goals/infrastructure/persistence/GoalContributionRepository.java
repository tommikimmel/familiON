package com.familion.finance.goals.infrastructure.persistence;

import com.familion.finance.goals.domain.GoalContribution;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {

    Optional<GoalContribution> findByIdAndSavingsGoalId(UUID id, UUID savingsGoalId);

    @Query("SELECT COALESCE(SUM(c.amountInGoalUnit), 0) FROM GoalContribution c WHERE c.savingsGoalId = :goalId")
    BigDecimal sumAmountInGoalUnitBySavingsGoalId(@Param("goalId") UUID goalId);
}
