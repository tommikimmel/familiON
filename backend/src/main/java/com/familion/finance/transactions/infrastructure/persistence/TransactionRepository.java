package com.familion.finance.transactions.infrastructure.persistence;

import com.familion.finance.transactions.domain.Transaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdAndFamilyIdAndDeletedAtIsNull(UUID id, UUID familyId);

    List<Transaction> findByFamilyIdAndTransferGroupIdAndDeletedAtIsNull(
            UUID familyId, UUID transferGroupId);
}
