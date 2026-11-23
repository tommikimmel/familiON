package com.familion.finance.transactions.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.NotFoundException;
import com.familion.common.events.FamilyFinancialDataChangedEvent;
import com.familion.common.money.Money;
import com.familion.economics.application.api.EconomicSnapshotData;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.economics.application.api.FamilyEconomicContext;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.transactions.application.api.ExpenseAttributedEvent;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionChangedEvent;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.finance.transactions.domain.EconomicSnapshot;
import com.familion.finance.transactions.domain.Transaction;
import com.familion.finance.transactions.infrastructure.persistence.EconomicSnapshotRepository;
import com.familion.finance.transactions.infrastructure.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionCommandService implements TransactionCommandApi {

    private final TransactionRepository transactionRepository;
    private final EconomicSnapshotRepository economicSnapshotRepository;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TransactionCommandService(
            TransactionRepository transactionRepository,
            EconomicSnapshotRepository economicSnapshotRepository,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.economicSnapshotRepository = economicSnapshotRepository;
        this.familyQueryApi = familyQueryApi;
        this.economicsApi = economicsApi;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID record(NewTransactionCommand cmd) {
        Objects.requireNonNull(cmd, "cmd must not be null");
        Objects.requireNonNull(cmd.familyId(), "familyId must not be null");
        Objects.requireNonNull(cmd.userId(), "userId must not be null");
        Objects.requireNonNull(cmd.accountId(), "accountId must not be null");
        Objects.requireNonNull(cmd.type(), "type must not be null");

        BigDecimal scaledAmount = requirePositiveAmount(cmd.amount());
        if (cmd.currencyCode() == null || cmd.currencyCode().isBlank()) {
            throw new BusinessRuleException("Currency code is required");
        }
        String currencyCode = cmd.currencyCode().trim().toUpperCase(Locale.ROOT);

        UUID attributedTo = cmd.attributedToUserId() != null ? cmd.attributedToUserId() : cmd.userId();
        familyQueryApi.requireMember(cmd.familyId(), cmd.userId());
        if (!attributedTo.equals(cmd.userId())) {
            familyQueryApi.requireMember(cmd.familyId(), attributedTo);
        }

        FamilySettings settings = familyQueryApi.getSettings(cmd.familyId());
        LocalDate transactionDate = cmd.transactionDate() != null
                ? cmd.transactionDate()
                : LocalDate.now(clock.withZone(settings.timezone()));
        Scope scope = cmd.scope() != null ? cmd.scope() : Scope.PERSONAL;
        TransactionStatus status = cmd.status() != null ? cmd.status() : TransactionStatus.POSTED;

        Transaction transaction = Transaction.create(
                cmd.familyId(),
                cmd.userId(),
                attributedTo,
                cmd.accountId(),
                cmd.categoryId(),
                cmd.type(),
                scope,
                status,
                scaledAmount,
                currencyCode,
                transactionDate,
                cmd.description(),
                cmd.notes(),
                cmd.installmentPlanId(),
                cmd.installmentNumber(),
                cmd.recurringExpenseId(),
                cmd.currencyExchangeId(),
                cmd.allowanceId(),
                cmd.receiptAttachmentId(),
                cmd.transferGroupId());

        Transaction saved = transactionRepository.saveAndFlush(transaction);

        EconomicSnapshotData snapshotData = computeSnapshot(settings, transactionDate, scaledAmount, currencyCode);
        economicSnapshotRepository.saveAndFlush(EconomicSnapshot.from(saved.getId(), snapshotData));

        if (cmd.type() == TransactionType.EXPENSE && !cmd.userId().equals(attributedTo)) {
            eventPublisher.publishEvent(new ExpenseAttributedEvent(
                    cmd.familyId(),
                    saved.getId(),
                    cmd.userId(),
                    attributedTo,
                    saved.getAmount(),
                    saved.getCurrencyCode()));
        }
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(cmd.familyId()));

        return saved.getId();
    }

    @Override
    @Transactional
    public void updateAmountAndDate(
            UUID familyId, UUID transactionId, BigDecimal amount, LocalDate date) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        BigDecimal scaledAmount = requirePositiveAmount(amount);
        if (date == null) {
            throw new BusinessRuleException("Transaction date is required");
        }

        Transaction transaction = transactionRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(transactionId, familyId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        transaction.updateAmountAndDate(scaledAmount, date);
        transactionRepository.saveAndFlush(transaction);

        FamilySettings settings = familyQueryApi.getSettings(familyId);
        upsertSnapshot(transaction, settings);

        eventPublisher.publishEvent(new TransactionChangedEvent(
                familyId,
                transaction.getId(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                false));
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    @Override
    @Transactional
    public void changeStatus(UUID familyId, UUID transactionId, TransactionStatus status) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(status, "status must not be null");

        Transaction transaction = transactionRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(transactionId, familyId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        transaction.changeStatus(status);
        transactionRepository.saveAndFlush(transaction);

        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    @Override
    @Transactional
    public void softDelete(UUID familyId, UUID transactionId) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");

        Transaction transaction = transactionRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(transactionId, familyId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        transaction.softDelete(Instant.now(clock));
        transactionRepository.saveAndFlush(transaction);

        eventPublisher.publishEvent(new TransactionChangedEvent(
                familyId,
                transaction.getId(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                true));
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    void upsertSnapshot(Transaction transaction, FamilySettings settings) {
        EconomicSnapshotData snapshotData = computeSnapshot(
                settings,
                transaction.getTransactionDate(),
                transaction.getAmount(),
                transaction.getCurrencyCode());
        EconomicSnapshot snapshot = economicSnapshotRepository
                .findById(transaction.getId())
                .map(existing -> {
                    existing.updateFrom(snapshotData);
                    return existing;
                })
                .orElseGet(() -> EconomicSnapshot.from(transaction.getId(), snapshotData));
        economicSnapshotRepository.saveAndFlush(snapshot);
    }

    private EconomicSnapshotData computeSnapshot(
            FamilySettings settings, LocalDate date, BigDecimal amount, String currencyCode) {
        FamilyEconomicContext ctx = new FamilyEconomicContext(
                settings.baseCurrency(),
                settings.referenceCurrency(),
                settings.referenceRateSource());
        return economicsApi.snapshotFor(ctx, date, amount, currencyCode);
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Transaction amount must be greater than zero");
        }
        BigDecimal scaled = Money.scale(amount);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Transaction amount must be greater than zero");
        }
        return scaled;
    }
}
