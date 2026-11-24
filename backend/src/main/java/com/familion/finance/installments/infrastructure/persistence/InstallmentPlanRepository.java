package com.familion.finance.installments.infrastructure.persistence;

import com.familion.finance.installments.domain.InstallmentPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, UUID> {

    Optional<InstallmentPlan> findByIdAndFamilyId(UUID id, UUID familyId);
}
