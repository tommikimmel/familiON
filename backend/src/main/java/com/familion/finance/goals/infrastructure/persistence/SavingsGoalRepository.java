package com.familion.finance.goals.infrastructure.persistence;

import com.familion.finance.goals.domain.SavingsGoal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    Optional<SavingsGoal> findByIdAndFamilyId(UUID id, UUID familyId);
}
