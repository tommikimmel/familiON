package com.familion.auth.infrastructure.persistence;

import com.familion.auth.domain.TrustedDevice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {

    Optional<TrustedDevice> findByDeviceTokenHash(String deviceTokenHash);

    List<TrustedDevice> findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId, Instant now);

    Optional<TrustedDevice> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TrustedDevice td SET td.revokedAt = :revokedAt "
            + "WHERE td.userId = :userId AND td.revokedAt IS NULL")
    int revokeAllActiveByUserId(
            @Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
