package com.familion.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.family.allowances.application.api.AllowanceDisbursedEvent;
import com.familion.finance.transactions.application.api.ExpenseAttributedEvent;
import com.familion.notifications.domain.Notification;
import com.familion.notifications.domain.NotificationCategory;
import com.familion.notifications.infrastructure.persistence.NotificationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-"
        })
class NotificationEventListenerIT extends IntegrationTestBase {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    private UUID familyId;
    private UUID adultId;
    private UUID childId;

    @BeforeEach
    void setUp() {
        familyId = fixtures.createFamily("Event Family " + UUID.randomUUID());
        adultId = fixtures.createUser(familyId, "tomas_" + UUID.randomUUID() + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childId = fixtures.createUser(familyId, "hijo_" + UUID.randomUUID() + "@familion.local", Role.HIJO, FamilyVisibility.FAMILY_FULL);
    }

    @Test
    void onExpenseAttributed_whenCommitted_createsNotificationForChild() {
        UUID txId = UUID.randomUUID();
        ExpenseAttributedEvent event =
                new ExpenseAttributedEvent(
                        familyId,
                        txId,
                        adultId,
                        childId,
                        new BigDecimal("20000.00"),
                        "ARS");

        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null;
        });

        List<Notification> childNotifs =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(childId, Pageable.unpaged()).getContent();
        assertThat(childNotifs).hasSize(1);

        Notification n = childNotifs.get(0);
        assertThat(n.getCategory()).isEqualTo(NotificationCategory.EXPENSE_ATTRIBUTED);
        assertThat(n.getTitle()).contains("registró $20.000 a tu nombre");
        assertThat(n.getBody()).contains("registró $20.000 a tu nombre");
        assertThat(n.getMetadata()).isNotNull();
        assertThat(n.getMetadata().get("link")).isEqualTo("/transactions?id=" + txId);
        assertThat(n.getMetadata().get("dedupeKey")).isEqualTo("expense-attributed:" + txId);
    }

    @Test
    void onExpenseAttributed_whenRollback_doesNotCreateNotification() {
        UUID txId = UUID.randomUUID();
        ExpenseAttributedEvent event =
                new ExpenseAttributedEvent(
                        familyId,
                        txId,
                        adultId,
                        childId,
                        new BigDecimal("5000.00"),
                        "ARS");

        assertThatThrownBy(
                () ->
                        transactionTemplate.execute(
                                status -> {
                                    eventPublisher.publishEvent(event);
                                    throw new RuntimeException("Simulated transaction failure");
                                }))
                .hasMessage("Simulated transaction failure");

        List<Notification> childNotifs =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(childId, Pageable.unpaged()).getContent();
        assertThat(childNotifs).isEmpty();
    }

    @Test
    void onAllowanceDisbursed_createsNotificationForChild() {
        UUID allowanceId = UUID.randomUUID();
        AllowanceDisbursedEvent event =
                new AllowanceDisbursedEvent(
                        familyId,
                        allowanceId,
                        childId,
                        new BigDecimal("15000.00"),
                        "ARS");

        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(event);
            return null;
        });

        List<Notification> childNotifs =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(childId, Pageable.unpaged()).getContent();
        assertThat(childNotifs).hasSize(1);

        Notification n = childNotifs.get(0);
        assertThat(n.getCategory()).isEqualTo(NotificationCategory.ALLOWANCE);
        assertThat(n.getTitle()).isEqualTo("Recibiste tu mensualidad de $15.000");
        assertThat(n.getBody()).isEqualTo("Recibiste tu mensualidad de $15.000");
        assertThat(n.getMetadata().get("link")).isEqualTo("/allowances");
        assertThat(n.getMetadata().get("dedupeKey")).isEqualTo("allowance:" + allowanceId);
    }
}
