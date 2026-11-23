package com.familion.finance.transactions.application;

import com.familion.common.error.ConflictException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.events.FamilyFinancialDataChangedEvent;
import com.familion.common.money.Money;
import com.familion.common.security.CurrentUser;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.accounts.application.api.AccountRef;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.transactions.application.api.ExpenseAttributedEvent;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionChangedEvent;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.finance.transactions.domain.Lens;
import com.familion.finance.transactions.domain.Transaction;
import com.familion.finance.transactions.domain.TransactionAttributionPolicy;
import com.familion.finance.transactions.domain.TransactionCurrencyPolicy;
import com.familion.finance.transactions.infrastructure.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionCommandService transactionCommandService;
    private final TransactionQueryService transactionQueryService;
    private final TransactionRepository transactionRepository;
    private final AccountQueryApi accountQueryApi;
    private final CategoryQueryApi categoryQueryApi;
    private final FamilyQueryApi familyQueryApi;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TransactionService(
            TransactionCommandService transactionCommandService,
            TransactionQueryService transactionQueryService,
            TransactionRepository transactionRepository,
            AccountQueryApi accountQueryApi,
            CategoryQueryApi categoryQueryApi,
            FamilyQueryApi familyQueryApi,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.transactionCommandService = transactionCommandService;
        this.transactionQueryService = transactionQueryService;
        this.transactionRepository = transactionRepository;
        this.accountQueryApi = accountQueryApi;
        this.categoryQueryApi = categoryQueryApi;
        this.familyQueryApi = familyQueryApi;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public TransactionItemView create(CurrentUser user, CreateTransactionCommand command) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(command, "command must not be null");

        BigDecimal scaledAmount = validatePositiveAmount(command.amount());
        String currencyCode = normalizeCurrency(command.currencyCode());
        FamilySettings settings = familyQueryApi.getSettings(user.familyId());

        LocalDate transactionDate = command.transactionDate() != null
                ? command.transactionDate()
                : LocalDate.now(clock.withZone(settings.timezone()));

        UUID attributedTo = TransactionAttributionPolicy.resolveAttributedTo(
                user, command.attributedToUserId());
        Scope scope = TransactionAttributionPolicy.resolveScopeForCreate(user, command.scope());

        if (command.type() == CreateTransactionType.TRANSFER) {
            return createTransfer(
                    user,
                    command,
                    settings,
                    scaledAmount,
                    currencyCode,
                    transactionDate,
                    attributedTo,
                    scope);
        }

        AccountRef account = accountQueryApi.requireUsable(user, command.accountId());
        TransactionCurrencyPolicy.validateForAccount(
                account, currencyCode, settings.enabledCurrencies());

        String normalizedDescription = normalizeOptionalText(command.description());
        String normalizedNotes = normalizeOptionalText(command.notes());

        if (command.categoryId() != null) {
            categoryQueryApi.requireUsable(
                    user.familyId(), command.categoryId(), command.type().name());
        } else if (normalizedDescription == null) {
            throw new InvalidTransactionRequestException(
                    "description", "Description is required when no category is provided");
        }

        TransactionType transactionType = command.type() == CreateTransactionType.EXPENSE
                ? TransactionType.EXPENSE
                : TransactionType.INCOME;

        UUID createdId = transactionCommandService.record(new NewTransactionCommand(
                user.familyId(),
                user.userId(),
                attributedTo,
                account.id(),
                command.categoryId(),
                transactionType,
                scope,
                TransactionStatus.POSTED,
                scaledAmount,
                currencyCode,
                transactionDate,
                normalizedDescription,
                normalizedNotes,
                null,
                null,
                null,
                null,
                null,
                command.receiptAttachmentId(),
                null));

        return transactionQueryService.getItemByIdInternal(
                user.familyId(), createdId, Lens.NOMINAL, settings);
    }

    @Transactional
    public TransactionItemView update(
            CurrentUser user, UUID transactionId, TransactionPatchCommand command) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        Transaction transaction = requireActiveTransaction(user.familyId(), transactionId);
        requireAuthorOrAdult(user, transaction);

        TransactionAttributionPolicy.requireCanChangeAttribution(
                user, transaction.getAttributedToUserId(), command.attributedToUserId());
        TransactionAttributionPolicy.requireCanSetScope(user, command.scope());

        BigDecimal scaledAmount =
                command.amount() != null ? validatePositiveAmount(command.amount()) : null;
        FamilySettings settings = familyQueryApi.getSettings(user.familyId());

        if (transaction.isExchangeOrAllowance()) {
            updateExchangeOrAllowance(transaction, command);
        } else if (transaction.isTransfer()) {
            updateTransfer(user.familyId(), transaction, command, scaledAmount, settings);
        } else if (transaction.isInstallment()) {
            updateInstallment(user.familyId(), transaction, command);
        } else {
            updateStandard(user, transaction, command, scaledAmount, settings);
        }

        return transactionQueryService.getItemByIdInternal(
                user.familyId(), transaction.getId(), Lens.NOMINAL, settings);
    }

    @Transactional
    public void delete(CurrentUser user, UUID transactionId) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(transactionId, "transactionId must not be null");

        Transaction transaction = requireActiveTransaction(user.familyId(), transactionId);
        requireAuthorOrAdult(user, transaction);

        if (transaction.isInstallment()) {
            throw new ConflictException("Use cancel-remaining");
        }

        if (transaction.isTransfer() && transaction.getTransferGroupId() != null) {
            List<Transaction> legs = transactionRepository
                    .findByFamilyIdAndTransferGroupIdAndDeletedAtIsNull(
                            user.familyId(), transaction.getTransferGroupId());
            if (legs.isEmpty()) {
                transactionCommandService.softDelete(user.familyId(), transaction.getId());
            } else {
                for (Transaction leg : legs) {
                    transactionCommandService.softDelete(user.familyId(), leg.getId());
                }
            }
            return;
        }

        transactionCommandService.softDelete(user.familyId(), transaction.getId());
    }

    private void updateExchangeOrAllowance(
            Transaction transaction, TransactionPatchCommand command) {
        if (command.amount() != null
                || command.transactionDate() != null
                || command.categoryIdPresent()
                || command.accountId() != null
                || command.attributedToUserId() != null
                || command.scope() != null) {
            throw new ConflictException(
                    "Only description and notes can be edited for "
                            + transaction.getType()
                            + " transactions");
        }
        if (command.descriptionPresent()) {
            transaction.updateDescription(command.description());
        }
        if (command.notesPresent()) {
            transaction.updateNotes(command.notes());
        }
        transactionRepository.saveAndFlush(transaction);
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(transaction.getFamilyId()));
    }

    private void updateTransfer(
            UUID familyId,
            Transaction transaction,
            TransactionPatchCommand command,
            BigDecimal scaledAmount,
            FamilySettings settings) {
        if (command.categoryIdPresent()
                || command.accountId() != null
                || command.attributedToUserId() != null
                || command.scope() != null) {
            throw new ConflictException(
                    "Only description, notes, amount, and transactionDate can be edited for transfer transactions");
        }

        List<Transaction> legs = transaction.getTransferGroupId() != null
                ? transactionRepository.findByFamilyIdAndTransferGroupIdAndDeletedAtIsNull(
                        familyId, transaction.getTransferGroupId())
                : List.of(transaction);
        if (legs.isEmpty()) {
            legs = List.of(transaction);
        }

        for (Transaction leg : legs) {
            boolean economicChanged = false;
            if (scaledAmount != null && scaledAmount.compareTo(leg.getAmount()) != 0) {
                leg.updateAmount(scaledAmount);
                economicChanged = true;
            }
            if (command.transactionDate() != null
                    && !command.transactionDate().equals(leg.getTransactionDate())) {
                leg.updateTransactionDate(command.transactionDate());
                economicChanged = true;
            }
            if (command.descriptionPresent()) {
                leg.updateDescription(command.description());
            }
            if (command.notesPresent()) {
                leg.updateNotes(command.notes());
            }
            transactionRepository.saveAndFlush(leg);

            if (economicChanged) {
                transactionCommandService.upsertSnapshot(leg, settings);
                eventPublisher.publishEvent(new TransactionChangedEvent(
                        familyId, leg.getId(), leg.getAmount(), leg.getTransactionDate(), false));
            }
        }
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    private void updateInstallment(
            UUID familyId, Transaction transaction, TransactionPatchCommand command) {
        if (command.amount() != null
                || command.transactionDate() != null
                || command.accountId() != null
                || command.attributedToUserId() != null
                || command.scope() != null) {
            throw new ConflictException(
                    "Only description, notes, and categoryId can be edited for installment transactions");
        }
        if (command.categoryIdPresent()) {
            if (command.categoryId() != null) {
                categoryQueryApi.requireUsable(
                        familyId, command.categoryId(), transaction.getType().name());
            }
            transaction.updateCategoryId(command.categoryId());
        }
        if (command.descriptionPresent()) {
            transaction.updateDescription(command.description());
        }
        if (command.notesPresent()) {
            transaction.updateNotes(command.notes());
        }
        requireCategoryOrDescription(transaction);
        transactionRepository.saveAndFlush(transaction);
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    private void updateStandard(
            CurrentUser user,
            Transaction transaction,
            TransactionPatchCommand command,
            BigDecimal scaledAmount,
            FamilySettings settings) {
        UUID familyId = user.familyId();
        boolean economicChanged = false;
        boolean newlyAttributed = false;

        if (command.accountId() != null && !command.accountId().equals(transaction.getAccountId())) {
            AccountRef newAccount = accountQueryApi.requireUsable(user, command.accountId());
            TransactionCurrencyPolicy.validateForAccount(
                    newAccount, transaction.getCurrencyCode(), settings.enabledCurrencies());
            transaction.updateAccountId(newAccount.id());
            economicChanged = true;
        }
        if (command.categoryIdPresent()) {
            if (command.categoryId() != null) {
                categoryQueryApi.requireUsable(
                        familyId, command.categoryId(), transaction.getType().name());
            }
            transaction.updateCategoryId(command.categoryId());
        }
        if (command.descriptionPresent()) {
            transaction.updateDescription(command.description());
        }
        if (command.notesPresent()) {
            transaction.updateNotes(command.notes());
        }
        requireCategoryOrDescription(transaction);

        if (command.attributedToUserId() != null
                && !command.attributedToUserId().equals(transaction.getAttributedToUserId())) {
            familyQueryApi.requireMember(familyId, command.attributedToUserId());
            transaction.updateAttributedToUserId(command.attributedToUserId());
            if (transaction.getType() == TransactionType.EXPENSE
                    && !command.attributedToUserId().equals(transaction.getUserId())) {
                newlyAttributed = true;
            }
        }
        if (command.scope() != null) {
            transaction.updateScope(command.scope());
        }
        if (scaledAmount != null && scaledAmount.compareTo(transaction.getAmount()) != 0) {
            transaction.updateAmount(scaledAmount);
            economicChanged = true;
        }
        if (command.transactionDate() != null
                && !command.transactionDate().equals(transaction.getTransactionDate())) {
            transaction.updateTransactionDate(command.transactionDate());
            economicChanged = true;
        }

        transactionRepository.saveAndFlush(transaction);

        if (economicChanged) {
            transactionCommandService.upsertSnapshot(transaction, settings);
            eventPublisher.publishEvent(new TransactionChangedEvent(
                    familyId,
                    transaction.getId(),
                    transaction.getAmount(),
                    transaction.getTransactionDate(),
                    false));
        }
        if (newlyAttributed) {
            eventPublisher.publishEvent(new ExpenseAttributedEvent(
                    familyId,
                    transaction.getId(),
                    transaction.getUserId(),
                    transaction.getAttributedToUserId(),
                    transaction.getAmount(),
                    transaction.getCurrencyCode()));
        }
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    private TransactionItemView createTransfer(
            CurrentUser user,
            CreateTransactionCommand command,
            FamilySettings settings,
            BigDecimal scaledAmount,
            String currencyCode,
            LocalDate transactionDate,
            UUID attributedTo,
            Scope scope) {
        if (command.targetAccountId() == null) {
            throw new InvalidTransactionRequestException(
                    "targetAccountId", "Target account is required for transfers");
        }

        AccountRef sourceAccount = accountQueryApi.requireUsable(user, command.accountId());
        AccountRef targetAccount = accountQueryApi.requireUsable(user, command.targetAccountId());

        TransactionCurrencyPolicy.validateForTransfer(
                sourceAccount, targetAccount, currencyCode, settings.enabledCurrencies());

        String normalizedDescription = normalizeOptionalText(command.description());
        String normalizedNotes = normalizeOptionalText(command.notes());
        UUID transferGroupId = UUID.randomUUID();

        UUID outId = transactionCommandService.record(new NewTransactionCommand(
                user.familyId(),
                user.userId(),
                attributedTo,
                sourceAccount.id(),
                null,
                TransactionType.TRANSFER_OUT,
                scope,
                TransactionStatus.POSTED,
                scaledAmount,
                currencyCode,
                transactionDate,
                normalizedDescription,
                normalizedNotes,
                null,
                null,
                null,
                null,
                null,
                command.receiptAttachmentId(),
                transferGroupId));

        transactionCommandService.record(new NewTransactionCommand(
                user.familyId(),
                user.userId(),
                attributedTo,
                targetAccount.id(),
                null,
                TransactionType.TRANSFER_IN,
                scope,
                TransactionStatus.POSTED,
                scaledAmount,
                currencyCode,
                transactionDate,
                normalizedDescription,
                normalizedNotes,
                null,
                null,
                null,
                null,
                null,
                command.receiptAttachmentId(),
                transferGroupId));

        return transactionQueryService.getItemByIdInternal(
                user.familyId(), outId, Lens.NOMINAL, settings);
    }

    private Transaction requireActiveTransaction(UUID familyId, UUID transactionId) {
        return transactionRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(transactionId, familyId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
    }

    private static void requireAuthorOrAdult(CurrentUser user, Transaction transaction) {
        if (!user.isAdult() && !user.userId().equals(transaction.getUserId())) {
            throw new ForbiddenException(
                    "Only the transaction author or an adult can modify this transaction");
        }
    }

    private static void requireCategoryOrDescription(Transaction transaction) {
        if (transaction.getCategoryId() == null && transaction.getDescription() == null) {
            throw new InvalidTransactionRequestException(
                    "description", "Description is required when no category is provided");
        }
    }

    private static BigDecimal validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionRequestException(
                    "amount", "Amount must be greater than zero");
        }
        BigDecimal scaled = Money.scale(amount);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionRequestException(
                    "amount", "Amount must be greater than zero");
        }
        return scaled;
    }

    private static String normalizeCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new InvalidTransactionRequestException(
                    "currencyCode", "Currency code is required");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
