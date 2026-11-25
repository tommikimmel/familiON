package com.familion.receipts.infrastructure.persistence;

import com.familion.receipts.domain.ReceiptAttachment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptAttachmentRepository extends JpaRepository<ReceiptAttachment, UUID> {

    Optional<ReceiptAttachment> findByIdAndFamilyId(UUID id, UUID familyId);
}
