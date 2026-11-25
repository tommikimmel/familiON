package com.familion.notifications.infrastructure.external;

import com.familion.common.events.FamilyActivityEvent;
import com.familion.common.security.Role;
import com.familion.family.allowances.application.api.AllowanceDisbursedEvent;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.transactions.application.api.ExpenseAttributedEvent;
import com.familion.notifications.application.ActivityDescriber;
import com.familion.notifications.application.NotificationService;
import com.familion.notifications.domain.NotificationCategory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final FamilyQueryApi familyQueryApi;

    public NotificationEventListener(
            NotificationService notificationService, FamilyQueryApi familyQueryApi) {
        this.notificationService = notificationService;
        this.familyQueryApi = familyQueryApi;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onExpenseAttributed(ExpenseAttributedEvent event) {
        try {
            String payerName;
            try {
                MemberRef member = familyQueryApi.requireMember(event.familyId(), event.paidByUserId());
                payerName = member.fullName();
            } catch (Exception e) {
                log.warn("Could not find payer member name: {}", e.getMessage());
                payerName = "Un miembro";
            }

            String formattedAmount = AmountFormatter.format(event.amount(), event.currencyCode());
            String text = payerName + " registró " + formattedAmount + " a tu nombre";
            String link = "/transactions?id=" + event.transactionId();
            String dedupeKey = "expense-attributed:" + event.transactionId();

            Map<String, Object> metadata = Map.of("link", link, "dedupeKey", dedupeKey);

            notificationService.notify(
                    event.attributedToUserId(),
                    event.familyId(),
                    NotificationCategory.EXPENSE_ATTRIBUTED,
                    text,
                    text,
                    metadata);
        } catch (Exception e) {
            log.error("Failed to handle ExpenseAttributedEvent", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAllowanceDisbursed(AllowanceDisbursedEvent event) {
        try {
            String formattedAmount = AmountFormatter.format(event.amount(), event.currencyCode());
            String text = "Recibiste tu mensualidad de " + formattedAmount;
            String link = "/allowances";
            String dedupeKey = "allowance:" + event.allowanceId();

            Map<String, Object> metadata = Map.of("link", link, "dedupeKey", dedupeKey);

            notificationService.notify(
                    event.childUserId(),
                    event.familyId(),
                    NotificationCategory.ALLOWANCE,
                    text,
                    text,
                    metadata);
        } catch (Exception e) {
            log.error("Failed to handle AllowanceDisbursedEvent", e);
        }
    }

    /** Published by the web layer after the request's transaction committed, so a plain listener is enough. */
    @EventListener
    public void onFamilyActivity(FamilyActivityEvent event) {
        try {
            ActivityDescriber.Activity activity =
                    ActivityDescriber.describe(event.method(), event.path()).orElse(null);
            if (activity == null || event.familyId() == null) {
                return;
            }
            List<MemberRef> members = familyQueryApi.listMembers(event.familyId());
            String actorName = members.stream()
                    .filter(m -> m.userId().equals(event.actorUserId()))
                    .map(MemberRef::fullName)
                    .findFirst()
                    .orElse("Un miembro");
            String title = truncate(actorName + " " + activity.phrase(), 140);
            String body = event.label() != null ? event.label() : title;

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("actorUserId", event.actorUserId().toString());
            metadata.put("action", event.method());
            metadata.put("resource", event.path());
            if (activity.link() != null) {
                metadata.put("link", activity.link());
            }

            for (MemberRef member : members) {
                boolean isActor = member.userId().equals(event.actorUserId());
                // shortcut: children never see others' activity (MemberRef has no visibility);
                // expose visibility on MemberRef if FAMILY_FULL children should get the feed.
                if (!isActor && (activity.actorOnly() || member.role() != Role.ADULTO)) {
                    continue;
                }
                notificationService
                        .notify(member.userId(), event.familyId(), NotificationCategory.ACTIVITY, title, body, metadata)
                        // The actor's own changes show in their feed but never count as unread.
                        .filter(n -> isActor)
                        .ifPresent(n -> notificationService.markAsRead(member.userId(), n.getId()));
            }
        } catch (Exception e) {
            log.error("Failed to handle FamilyActivityEvent", e);
        }
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
