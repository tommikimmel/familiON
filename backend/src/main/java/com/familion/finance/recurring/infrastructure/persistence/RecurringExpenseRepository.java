package com.familion.finance.recurring.infrastructure.persistence;

import com.familion.finance.recurring.domain.RecurringExpense;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {

    Optional<RecurringExpense> findByIdAndFamilyId(UUID id, UUID familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RecurringExpense r WHERE r.id = :id AND r.familyId = :familyId")
    Optional<RecurringExpense> findByIdAndFamilyIdForUpdate(
            @Param("id") UUID id, @Param("familyId") UUID familyId);
}
