package com.familion.notifications.application;

import com.familion.notifications.domain.PushSubscription;
import com.familion.notifications.infrastructure.persistence.PushSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;

    public PushSubscriptionService(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    public void saveSubscription(
            UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        repository
                .findByEndpoint(endpoint)
                .ifPresentOrElse(
                        existing -> existing.activate(userId, p256dh, auth, userAgent),
                        () -> {
                            PushSubscription sub =
                                    new PushSubscription(
                                            userId,
                                            endpoint,
                                            p256dh,
                                            auth,
                                            userAgent,
                                            true,
                                            Instant.now());
                            repository.save(sub);
                        });
    }

    public void deactivateSubscription(UUID userId, String endpoint) {
        repository
                .findByUserIdAndEndpoint(userId, endpoint)
                .ifPresent(PushSubscription::deactivate);
    }

    public void deactivateById(UUID id) {
        repository.findById(id).ifPresent(PushSubscription::deactivate);
    }

    @Transactional(readOnly = true)
    public List<PushSubscription> findActiveByUserId(UUID userId) {
        return repository.findByUserIdAndIsActiveTrue(userId);
    }
}
