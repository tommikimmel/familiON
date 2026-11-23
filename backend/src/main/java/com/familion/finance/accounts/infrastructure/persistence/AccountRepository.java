package com.familion.finance.accounts.infrastructure.persistence;

import com.familion.finance.accounts.domain.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIdAndFamilyId(UUID id, UUID familyId);

    Optional<Account> findByIdAndFamilyIdAndDeletedAtIsNull(UUID id, UUID familyId);

    boolean existsByFamilyIdAndNameIgnoreCaseAndActiveTrueAndDeletedAtIsNull(
            UUID familyId, String name);

    boolean existsByFamilyIdAndNameIgnoreCaseAndActiveTrueAndDeletedAtIsNullAndIdNot(
            UUID familyId, String name, UUID id);
}
