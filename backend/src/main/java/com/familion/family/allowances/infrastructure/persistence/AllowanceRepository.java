package com.familion.family.allowances.infrastructure.persistence;

import com.familion.family.allowances.domain.Allowance;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AllowanceRepository extends JpaRepository<Allowance, UUID> {

    Optional<Allowance> findByIdAndFamilyId(UUID id, UUID familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Allowance a WHERE a.id = :id AND a.familyId = :familyId")
    Optional<Allowance> findByIdAndFamilyIdForUpdate(
            @Param("id") UUID id, @Param("familyId") UUID familyId);
}
