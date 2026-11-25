package com.familion.family.infrastructure.persistence;

import com.familion.family.domain.UserAvatar;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {

    @Query("select a.updatedAt from UserAvatar a where a.userId = :userId")
    Optional<Instant> findUpdatedAtByUserId(@Param("userId") UUID userId);
}
