package com.familion.family.infrastructure.persistence;

import com.familion.family.domain.FamilyCurrency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyCurrencyRepository extends JpaRepository<FamilyCurrency, UUID> {

    List<FamilyCurrency> findAllByFamilyIdAndActiveTrueOrderByCreatedAtAscCurrencyCodeAsc(
            UUID familyId);
}
