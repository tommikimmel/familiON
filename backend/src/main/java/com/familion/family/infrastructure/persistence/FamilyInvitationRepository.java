package com.familion.family.infrastructure.persistence;

import com.familion.family.domain.FamilyInvitation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyInvitationRepository extends JpaRepository<FamilyInvitation, UUID> {

    List<FamilyInvitation> findAllByFamilyIdAndAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID familyId, Instant now);

    Optional<FamilyInvitation> findByIdAndFamilyId(UUID id, UUID familyId);

    Optional<FamilyInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM FamilyInvitation i WHERE i.tokenHash = :tokenHash")
    Optional<FamilyInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
