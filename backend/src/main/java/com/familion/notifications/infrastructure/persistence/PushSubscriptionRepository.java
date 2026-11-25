package com.familion.notifications.infrastructure.persistence;

import com.familion.notifications.domain.PushSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByUserIdAndIsActiveTrue(UUID userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByUserIdAndEndpoint(UUID userId, String endpoint);
}
