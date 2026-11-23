package com.familion.finance.transactions.application;

import com.familion.common.money.Money;
import com.familion.common.security.CurrentUser;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.accounts.application.api.AccountRef;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.finance.transactions.domain.Lens;
import com.familion.finance.transactions.domain.TransactionAttributionPolicy;
import com.familion.finance.transactions.domain.TransactionCurrencyPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionCommandApi transactionCommandApi;
    private final TransactionQueryService transactionQueryService;
    private final AccountQueryApi accountQueryApi;
    private final CategoryQueryApi categoryQueryApi;
    private final FamilyQueryApi familyQueryApi;
    private final Clock clock;

    public TransactionService(
            TransactionCommandApi transactionCommandApi,
            TransactionQueryService transactionQueryService,
            AccountQueryApi accountQueryApi,
            CategoryQueryApi categoryQueryApi,
            FamilyQueryApi familyQueryApi,
            Clock clock) {
        this.transactionCommandApi = transactionCommandApi;
        this.transactionQueryService = transactionQueryService;
        this.accountQueryApi = accountQueryApi;
        this.categoryQueryApi = categoryQueryApi;
        this.familyQueryApi = familyQueryApi;
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
                    user, command, settings, scaledAmount, currencyCode, transactionDate, attributedTo, scope);
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

        UUID createdId = transactionCommandApi.record(new NewTransactionCommand(
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

        UUID outId = transactionCommandApi.record(new NewTransactionCommand(
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

        transactionCommandApi.record(new NewTransactionCommand(
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
