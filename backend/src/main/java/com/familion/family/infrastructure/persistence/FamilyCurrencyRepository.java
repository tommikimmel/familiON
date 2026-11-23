package com.familion.family.infrastructure.persistence;

import com.familion.family.domain.FamilyCurrency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyCurrencyRepository extends JpaRepository<FamilyCurrency, UUID> {

    List<FamilyCurrency> findAllByFamilyIdAndActiveTrueOrderByCreatedAtAscCurrencyCodeAsc(
            UUID familyId);

    List<FamilyCurrency> findAllByFamilyIdOrderByCreatedAtAscCurrencyCodeAsc(UUID familyId);

    Optional<FamilyCurrency> findByFamilyIdAndCurrencyCode(UUID familyId, String currencyCode);

    boolean existsByFamilyIdAndCurrencyCode(UUID familyId, String currencyCode);
}
