package com.familion.notifications.infrastructure.external;

import com.familion.family.allowances.application.api.AllowanceDisbursedEvent;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.transactions.application.api.ExpenseAttributedEvent;
import com.familion.notifications.application.NotificationService;
import com.familion.notifications.domain.NotificationCategory;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
}
