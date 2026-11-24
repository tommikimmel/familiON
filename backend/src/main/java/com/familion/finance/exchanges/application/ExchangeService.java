package com.familion.finance.exchanges.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.money.Money;
import com.familion.common.security.CurrentUser;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.accounts.application.api.AccountRef;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.categories.application.api.CategoryRef;
import com.familion.finance.exchanges.domain.CurrencyExchange;
import com.familion.finance.exchanges.domain.ExchangeRateCalculator;
import com.familion.finance.exchanges.infrastructure.persistence.CurrencyExchangeRepository;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeService {

    static final String FINANCIAL_COSTS_SYSTEM_KEY = "FINANCIAL_COSTS";
    private static final int MAX_NOTES_LENGTH = 255;
    private static final int MAX_CURRENCY_LENGTH = 10;

    private final CurrencyExchangeRepository exchangeRepository;
    private final ExchangeReadQuery exchangeReadQuery;
    private final TransactionCommandApi transactionCommandApi;
    private final AccountQueryApi accountQueryApi;
    private final CategoryQueryApi categoryQueryApi;
    private final FamilyQueryApi familyQueryApi;
    private final Clock clock;
    private final ExchangeRateCalculator rateCalculator;

    public ExchangeService(
            CurrencyExchangeRepository exchangeRepository,
            ExchangeReadQuery exchangeReadQuery,
            TransactionCommandApi transactionCommandApi,
            AccountQueryApi accountQueryApi,
            CategoryQueryApi categoryQueryApi,
            FamilyQueryApi familyQueryApi,
            Clock clock) {
        this.exchangeRepository = exchangeRepository;
        this.exchangeReadQuery = exchangeReadQuery;
        this.transactionCommandApi = transactionCommandApi;
        this.accountQueryApi = accountQueryApi;
        this.categoryQueryApi = categoryQueryApi;
        this.familyQueryApi = familyQueryApi;
        this.clock = clock;
        this.rateCalculator = new ExchangeRateCalculator();
    }

    @Transactional
    public ExchangeView create(CurrentUser user, CreateExchangeCommand command) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(command, "command must not be null");

        if (command.sourceAccountId() == null || command.targetAccountId() == null) {
            throw new BusinessRuleException("Source and target accounts are required");
        }
        if (command.sourceAccountId().equals(command.targetAccountId())) {
            throw new BusinessRuleException("Source and target accounts must be different");
        }

        BigDecimal scaledSourceAmount = requirePositiveAmount(command.sourceAmount(), "Source amount");
        BigDecimal scaledTargetAmount = requirePositiveAmount(command.targetAmount(), "Target amount");
        BigDecimal scaledFeeAmount = resolveFeeAmount(command.feeAmount());
        String normalizedNotes = normalizeNotes(command.notes());

        AccountRef sourceAccount = accountQueryApi.requireUsable(user, command.sourceAccountId());
        AccountRef targetAccount = accountQueryApi.requireUsable(user, command.targetAccountId());

        String sourceCurrency = normalizeCurrency(sourceAccount.currencyCode());
        String targetCurrency = normalizeCurrency(targetAccount.currencyCode());

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        MemberRef author = familyQueryApi.requireMember(user.familyId(), user.userId());

        ExchangeRateCalculator.RateCalculation rateCalculation = rateCalculator.calculateAndValidate(
                sourceCurrency,
                targetCurrency,
                settings.baseCurrency(),
                scaledSourceAmount,
                scaledTargetAmount,
                command.appliedExchangeRate());

        LocalDate exchangeDate = command.exchangeDate() != null
                ? command.exchangeDate()
                : LocalDate.now(clock.withZone(settings.timezone()));

        boolean hasFee = scaledFeeAmount.compareTo(BigDecimal.ZERO) > 0;
        String resolvedFeeCurrency = hasFee ? resolveFeeCurrency(command.feeCurrency(), sourceCurrency) : null;

        CurrencyExchange exchange = CurrencyExchange.create(
                user.familyId(),
                user.userId(),
                sourceAccount.id(),
                targetAccount.id(),
                scaledSourceAmount,
                sourceCurrency,
                scaledTargetAmount,
                targetCurrency,
                rateCalculation.appliedExchangeRate(),
                scaledFeeAmount,
                resolvedFeeCurrency,
                exchangeDate,
                normalizedNotes,
                Instant.now(clock));

        CurrencyExchange saved = exchangeRepository.saveAndFlush(exchange);

        UUID transferGroupId = UUID.randomUUID();
        String exchangeDescription = "Currency exchange " + sourceCurrency + " -> " + targetCurrency;

        transactionCommandApi.record(new NewTransactionCommand(
                user.familyId(),
                user.userId(),
                user.userId(),
                sourceAccount.id(),
                null,
                TransactionType.EXCHANGE_OUT,
                Scope.PERSONAL,
                TransactionStatus.POSTED,
                scaledSourceAmount,
                sourceCurrency,
                exchangeDate,
                exchangeDescription,
                normalizedNotes,
                null,
                null,
                null,
                saved.getId(),
                null,
                null,
                transferGroupId));

        transactionCommandApi.record(new NewTransactionCommand(
                user.familyId(),
                user.userId(),
                user.userId(),
                targetAccount.id(),
                null,
                TransactionType.EXCHANGE_IN,
                Scope.PERSONAL,
                TransactionStatus.POSTED,
                scaledTargetAmount,
                targetCurrency,
                exchangeDate,
                exchangeDescription,
                normalizedNotes,
                null,
                null,
                null,
                saved.getId(),
                null,
                null,
                transferGroupId));

        if (hasFee) {
            CategoryRef feeCategory = categoryQueryApi.requireSystemCategory(FINANCIAL_COSTS_SYSTEM_KEY);
            transactionCommandApi.record(new NewTransactionCommand(
                    user.familyId(),
                    user.userId(),
                    user.userId(),
                    sourceAccount.id(),
                    feeCategory.id(),
                    TransactionType.EXPENSE,
                    Scope.PERSONAL,
                    TransactionStatus.POSTED,
                    scaledFeeAmount,
                    resolvedFeeCurrency,
                    exchangeDate,
                    "Exchange fee " + sourceCurrency + " -> " + targetCurrency,
                    normalizedNotes,
                    null,
                    null,
                    null,
                    saved.getId(),
                    null,
                    null,
                    null));
        }

        return new ExchangeView(
                saved.getId(),
                saved.getExchangeDate(),
                new ExchangeView.AccountSummary(sourceAccount.id(), sourceAccount.name()),
                new ExchangeView.AccountSummary(targetAccount.id(), targetAccount.name()),
                saved.getSourceAmount(),
                saved.getSourceCurrency(),
                saved.getTargetAmount(),
                saved.getTargetCurrency(),
                saved.getAppliedExchangeRate(),
                saved.getFeeAmount(),
                saved.getFeeCurrency(),
                saved.getNotes(),
                rateCalculation.operation(),
                new ExchangeView.UserSummary(author.userId(), author.fullName()));
    }

    @Transactional
    public void delete(CurrentUser user, UUID exchangeId) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");

        CurrencyExchange exchange = exchangeRepository
                .findByIdAndFamilyId(exchangeId, user.familyId())
                .orElseThrow(() -> new NotFoundException("Currency exchange not found"));

        if (!exchangeReadQuery.hasActiveExchangeIn(user.familyId(), exchangeId)) {
            throw new NotFoundException("Currency exchange not found");
        }

        if (!user.isAdult() && !user.userId().equals(exchange.getUserId())) {
            throw new ForbiddenException(
                    "Only the exchange author or an adult can delete this exchange");
        }

        List<UUID> activeTransactionIds =
                exchangeReadQuery.findActiveTransactionIds(user.familyId(), exchangeId);
        for (UUID transactionId : activeTransactionIds) {
            transactionCommandApi.softDelete(user.familyId(), transactionId);
        }
    }

    private static BigDecimal requirePositiveAmount(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(label + " must be greater than zero");
        }
        BigDecimal scaled = Money.scale(value);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(label + " must be greater than zero");
        }
        return scaled;
    }

    private static BigDecimal resolveFeeAmount(BigDecimal feeAmount) {
        if (feeAmount == null) {
            return Money.scale(BigDecimal.ZERO);
        }
        if (feeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Fee amount must be greater than or equal to zero");
        }
        return Money.scale(feeAmount);
    }

    private static String resolveFeeCurrency(String feeCurrency, String defaultCurrency) {
        if (feeCurrency == null || feeCurrency.isBlank()) {
            return defaultCurrency;
        }
        String normalized = feeCurrency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_CURRENCY_LENGTH) {
            throw new BusinessRuleException(
                    "Fee currency must be at most " + MAX_CURRENCY_LENGTH + " characters");
        }
        return normalized;
    }

    private static String normalizeCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new BusinessRuleException("Currency code is required");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_NOTES_LENGTH) {
            throw new BusinessRuleException(
                    "Notes must be at most " + MAX_NOTES_LENGTH + " characters");
        }
        return trimmed;
    }
}
