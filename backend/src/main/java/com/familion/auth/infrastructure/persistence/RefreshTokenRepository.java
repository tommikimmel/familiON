package com.familion.auth.infrastructure.persistence;

import com.familion.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :revokedAt "
            + "WHERE rt.userId = :userId AND rt.revokedAt IS NULL")
    int revokeAllActiveByUserId(
            @Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :revokedAt "
            + "WHERE rt.id = :id AND rt.revokedAt IS NULL")
    int revokeIfActive(@Param("id") UUID id, @Param("revokedAt") Instant revokedAt);
}
