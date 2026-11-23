package com.familion.auth.infrastructure.persistence;

import com.familion.auth.domain.EmailCodePurpose;
import com.familion.auth.domain.EmailSecurityCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailSecurityCodeRepository extends JpaRepository<EmailSecurityCode, UUID> {

    Optional<EmailSecurityCode> findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            UUID userId, EmailCodePurpose purpose);

    Optional<EmailSecurityCode> findFirstByPurposeAndCodeHashAndConsumedAtIsNullOrderByCreatedAtDesc(
            EmailCodePurpose purpose, String codeHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailSecurityCode c SET c.consumedAt = :consumedAt "
            + "WHERE c.userId = :userId AND c.purpose = :purpose AND c.consumedAt IS NULL")
    int consumeAllPending(
            @Param("userId") UUID userId,
            @Param("purpose") EmailCodePurpose purpose,
            @Param("consumedAt") Instant consumedAt);
}
