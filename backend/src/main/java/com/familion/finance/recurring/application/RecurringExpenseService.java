package com.familion.finance.recurring.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ConflictException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.events.FamilyFinancialDataChangedEvent;
import com.familion.common.money.Money;
import com.familion.common.security.CurrentUser;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.accounts.application.api.AccountRef;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.categories.application.api.CategoryRef;
import com.familion.finance.recurring.domain.AdjustmentRule;
import com.familion.finance.recurring.domain.RecurringAmountEstimator;
import com.familion.finance.recurring.domain.RecurringExpense;
import com.familion.finance.recurring.domain.RecurringMonthStatus;
import com.familion.finance.recurring.domain.RecurringSchedule;
import com.familion.finance.recurring.infrastructure.persistence.RecurringExpenseRepository;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringExpenseService {

    private static final String EXPENSE_CATEGORY_KIND = "EXPENSE";
    private static final String CREDIT_CARD_ACCOUNT_TYPE = "CREDIT_CARD";
    private static final int MAX_NAME_LENGTH = 120;

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RecurringReadQuery readQuery;
    private final CategoryQueryApi categoryQueryApi;
    private final AccountQueryApi accountQueryApi;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final TransactionCommandApi transactionCommandApi;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final RecurringAmountEstimator estimator;

    public RecurringExpenseService(
            RecurringExpenseRepository recurringExpenseRepository,
            RecurringReadQuery readQuery,
            CategoryQueryApi categoryQueryApi,
            AccountQueryApi accountQueryApi,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            TransactionCommandApi transactionCommandApi,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.readQuery = readQuery;
        this.categoryQueryApi = categoryQueryApi;
        this.accountQueryApi = accountQueryApi;
        this.familyQueryApi = familyQueryApi;
        this.economicsApi = economicsApi;
        this.transactionCommandApi = transactionCommandApi;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.estimator = new RecurringAmountEstimator();
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseView> list(
            CurrentUser currentUser, YearMonth requestedMonth, boolean includeInactive) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");

        FamilySettings settings = familyQueryApi.getSettings(currentUser.familyId());
        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        YearMonth targetMonth = requestedMonth != null ? requestedMonth : YearMonth.from(today);

        List<RecurringReadRow> rows = readQuery.findVisibleForUser(
                currentUser.familyId(), currentUser.userId(), currentUser.isAdult(), includeInactive);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> allIds = rows.stream().map(RecurringReadRow::id).toList();
        Map<UUID, RecurringPaymentRow> paymentsInMonth = readQuery.findPaymentsInRange(
                allIds, targetMonth.atDay(1), targetMonth.atEndOfMonth());

        List<UUID> lastPaidIds = rows.stream()
                .filter(row -> row.adjustmentRule() == AdjustmentRule.LAST_PAID)
                .map(RecurringReadRow::id)
                .toList();
        Map<UUID, BigDecimal> lastPaidAmounts = lastPaidIds.isEmpty()
                ? Map.of()
                : readQuery.findLatestPaidAmountBefore(lastPaidIds, targetMonth.atDay(1));

        List<RecurringExpenseView> result = new ArrayList<>(rows.size());
        for (RecurringReadRow row : rows) {
            result.add(toView(
                    row,
                    targetMonth,
                    today,
                    settings,
                    paymentsInMonth.get(row.id()),
                    lastPaidAmounts.get(row.id())));
        }
        return List.copyOf(result);
    }

    @Transactional
    public RecurringExpenseView create(
            CurrentUser currentUser, CreateRecurringExpenseCommand command) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(command, "command must not be null");
        requireAdult(currentUser);

        String name = validateName(command.name());
        BigDecimal baseAmount = validatePositiveAmount("baseAmount", command.baseAmount());
        String currencyCode = validateCurrency(command.currencyCode());
        int dueDay = validateDueDay(command.dueDay());
        AdjustmentRule adjustmentRule =
                Objects.requireNonNull(command.adjustmentRule(), "adjustmentRule must not be null");
        LocalDate startDate =
                Objects.requireNonNull(command.startDate(), "startDate must not be null");
        LocalDate endDate = command.endDate();
        validateDateRange(startDate, endDate);
        Scope scope = command.scope() != null ? command.scope() : Scope.FAMILY;

        UUID familyId = currentUser.familyId();
        FamilySettings settings = familyQueryApi.getSettings(familyId);
        requireEnabledCurrency(settings, currencyCode);

        CategoryRef category = categoryQueryApi.requireUsable(
                familyId, command.categoryId(), EXPENSE_CATEGORY_KIND);

        AccountRef account = null;
        if (command.accountId() != null) {
            account = accountQueryApi.requireUsable(currentUser, command.accountId());
            validateAccountCurrency(account, currencyCode);
        }

        RecurringExpense entity = RecurringExpense.create(
                familyId,
                currentUser.userId(),
                account == null ? null : account.id(),
                category.id(),
                name,
                scope,
                baseAmount,
                currencyCode,
                dueDay,
                adjustmentRule,
                startDate,
                endDate);
        RecurringExpense saved = recurringExpenseRepository.saveAndFlush(entity);
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));

        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        YearMonth currentMonth = YearMonth.from(today);

        RecurringReadRow row = new RecurringReadRow(
                saved.getId(),
                saved.getFamilyId(),
                saved.getCreatedByUserId(),
                saved.getName(),
                category.id(),
                category.name(),
                category.icon(),
                category.colorHex(),
                account == null ? null : account.id(),
                account == null ? null : account.name(),
                saved.getScope(),
                saved.getBaseAmount(),
                saved.getCurrencyCode(),
                saved.getDueDay(),
                saved.getAdjustmentRule(),
                saved.getStartDate(),
                saved.getEndDate(),
                saved.isActive());

        return toView(row, currentMonth, today, settings, null, null);
    }

    @Transactional
    public RecurringExpenseView update(
            CurrentUser currentUser, UUID recurringExpenseId, UpdateRecurringExpenseCommand command) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(recurringExpenseId, "recurringExpenseId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        requireAdult(currentUser);

        UUID familyId = currentUser.familyId();
        RecurringExpense entity = recurringExpenseRepository
                .findByIdAndFamilyId(recurringExpenseId, familyId)
                .orElseThrow(() -> new NotFoundException("Recurring expense not found"));

        requireCanManage(currentUser, entity);

        String effectiveName =
                command.name() != null ? validateName(command.name()) : entity.getName();
        UUID effectiveCategoryId =
                command.categoryId() != null ? command.categoryId() : entity.getCategoryId();
        UUID effectiveAccountId =
                command.accountIdPresent() ? command.accountId() : entity.getAccountId();
        Scope effectiveScope = command.scope() != null ? command.scope() : entity.getScope();
        BigDecimal effectiveBaseAmount = command.baseAmount() != null
                ? validatePositiveAmount("baseAmount", command.baseAmount())
                : entity.getBaseAmount();
        String effectiveCurrencyCode = command.currencyCode() != null
                ? validateCurrency(command.currencyCode())
                : entity.getCurrencyCode();
        int effectiveDueDay = command.dueDay() != null
                ? validateDueDay(command.dueDay())
                : entity.getDueDay();
        AdjustmentRule effectiveRule = command.adjustmentRule() != null
                ? command.adjustmentRule()
                : entity.getAdjustmentRule();
        LocalDate effectiveStartDate =
                command.startDate() != null ? command.startDate() : entity.getStartDate();
        LocalDate effectiveEndDate =
                command.endDatePresent() ? command.endDate() : entity.getEndDate();
        boolean effectiveActive =
                command.active() != null ? command.active() : entity.isActive();

        validateDateRange(effectiveStartDate, effectiveEndDate);

        FamilySettings settings = familyQueryApi.getSettings(familyId);
        if (command.currencyCode() != null) {
            requireEnabledCurrency(settings, effectiveCurrencyCode);
        }

        if (command.categoryId() != null) {
            categoryQueryApi.requireUsable(familyId, effectiveCategoryId, EXPENSE_CATEGORY_KIND);
        }

        boolean shouldValidateAccount = effectiveAccountId != null
                && (command.accountIdPresent() || command.currencyCode() != null);
        if (shouldValidateAccount) {
            AccountRef account = accountQueryApi.requireUsable(currentUser, effectiveAccountId);
            validateAccountCurrency(account, effectiveCurrencyCode);
        }

        entity.update(
                effectiveName,
                effectiveCategoryId,
                effectiveAccountId,
                effectiveScope,
                effectiveBaseAmount,
                effectiveCurrencyCode,
                effectiveDueDay,
                effectiveRule,
                effectiveStartDate,
                effectiveEndDate,
                effectiveActive);
        recurringExpenseRepository.saveAndFlush(entity);
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));

        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        YearMonth currentMonth = YearMonth.from(today);
        return loadViewForMonth(familyId, entity.getId(), currentMonth, today, settings);
    }

    @Transactional
    public void deactivate(CurrentUser currentUser, UUID recurringExpenseId) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(recurringExpenseId, "recurringExpenseId must not be null");
        requireAdult(currentUser);

        UUID familyId = currentUser.familyId();
        RecurringExpense entity = recurringExpenseRepository
                .findByIdAndFamilyId(recurringExpenseId, familyId)
                .orElseThrow(() -> new NotFoundException("Recurring expense not found"));

        requireCanManage(currentUser, entity);

        entity.deactivate();
        recurringExpenseRepository.saveAndFlush(entity);
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    @Transactional
    public RecurringExpenseView confirmPayment(
            CurrentUser currentUser, UUID recurringExpenseId, ConfirmPaymentCommand command) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(recurringExpenseId, "recurringExpenseId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        YearMonth targetMonth = Objects.requireNonNull(command.month(), "month must not be null");
        BigDecimal scaledAmount = validatePositiveAmount("amount", command.amount());

        UUID familyId = currentUser.familyId();
        RecurringExpense entity = recurringExpenseRepository
                .findByIdAndFamilyIdForUpdate(recurringExpenseId, familyId)
                .orElseThrow(() -> new NotFoundException("Recurring expense not found"));

        requireCanConfirmPayment(currentUser, entity);

        if (!entity.isActive()) {
            throw new BusinessRuleException("Cannot confirm payment for an inactive recurring expense");
        }

        if (!RecurringSchedule.isApplicable(
                entity.getStartDate(), entity.getEndDate(), targetMonth)) {
            throw new BusinessRuleException(
                    "Recurring expense is not applicable for month " + targetMonth);
        }

        if (readQuery.existsPaymentInRange(
                familyId, entity.getId(), targetMonth.atDay(1), targetMonth.atEndOfMonth())) {
            throw new ConflictException(
                    "Recurring expense has already been paid for month " + targetMonth);
        }

        UUID effectiveAccountId =
                command.accountId() != null ? command.accountId() : entity.getAccountId();
        if (effectiveAccountId == null) {
            throw new BusinessRuleException("Account is required to confirm payment");
        }

        AccountRef account = accountQueryApi.requireUsable(currentUser, effectiveAccountId);
        validateAccountCurrency(account, entity.getCurrencyCode());

        FamilySettings settings = familyQueryApi.getSettings(familyId);
        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        LocalDate effectivePaymentDate = resolvePaymentDate(
                command.paymentDate(), targetMonth, entity.getDueDay(), today);

        transactionCommandApi.record(new NewTransactionCommand(
                familyId,
                currentUser.userId(),
                currentUser.userId(),
                account.id(),
                entity.getCategoryId(),
                TransactionType.EXPENSE,
                entity.getScope(),
                TransactionStatus.POSTED,
                scaledAmount,
                entity.getCurrencyCode(),
                effectivePaymentDate,
                entity.getName(),
                null,
                null,
                null,
                entity.getId(),
                null,
                null,
                null,
                null));

        return loadViewForMonth(familyId, entity.getId(), targetMonth, today, settings);
    }

    private RecurringExpenseView loadViewForMonth(
            UUID familyId,
            UUID recurringExpenseId,
            YearMonth targetMonth,
            LocalDate today,
            FamilySettings settings) {
        RecurringReadRow row = readQuery
                .findById(familyId, recurringExpenseId)
                .orElseThrow(() -> new NotFoundException("Recurring expense not found"));

        Map<UUID, RecurringPaymentRow> paymentsInMonth = readQuery.findPaymentsInRange(
                List.of(recurringExpenseId), targetMonth.atDay(1), targetMonth.atEndOfMonth());

        BigDecimal lastPaidAmount = null;
        if (row.adjustmentRule() == AdjustmentRule.LAST_PAID) {
            lastPaidAmount = readQuery
                    .findLatestPaidAmountBefore(List.of(recurringExpenseId), targetMonth.atDay(1))
                    .get(recurringExpenseId);
        }

        return toView(
                row,
                targetMonth,
                today,
                settings,
                paymentsInMonth.get(recurringExpenseId),
                lastPaidAmount);
    }

    private RecurringExpenseView toView(
            RecurringReadRow row,
            YearMonth targetMonth,
            LocalDate today,
            FamilySettings settings,
            RecurringPaymentRow payment,
            BigDecimal lastPaidBeforeMonth) {
        LocalDate dueDate = RecurringSchedule.dueDateForMonth(row.dueDay(), targetMonth);
        RecurringMonthStatus status = RecurringSchedule.resolveStatus(
                row.startDate(), row.endDate(), row.dueDay(), targetMonth, payment != null, today);

        BigDecimal estimatedAmount = estimator.estimate(
                row.adjustmentRule(),
                row.baseAmount(),
                row.currencyCode(),
                row.startDate(),
                targetMonth,
                today,
                settings.referenceCurrency(),
                settings.referenceRateSource(),
                lastPaidBeforeMonth,
                economicsApi);

        RecurringExpenseView.CategorySummary categorySummary =
                new RecurringExpenseView.CategorySummary(
                        row.categoryId(),
                        row.categoryName(),
                        row.categoryIcon(),
                        row.categoryColorHex());
        RecurringExpenseView.AccountSummary accountSummary = row.accountId() == null
                ? null
                : new RecurringExpenseView.AccountSummary(row.accountId(), row.accountName());
        RecurringExpenseView.MonthStatusView monthStatus =
                new RecurringExpenseView.MonthStatusView(
                        targetMonth,
                        dueDate,
                        estimatedAmount,
                        status,
                        payment == null ? null : payment.transactionId(),
                        payment == null ? null : payment.amount(),
                        payment == null ? null : payment.transactionDate());

        return new RecurringExpenseView(
                row.id(),
                row.name(),
                categorySummary,
                accountSummary,
                row.scope(),
                row.baseAmount(),
                row.currencyCode(),
                row.dueDay(),
                row.adjustmentRule(),
                row.startDate(),
                row.endDate(),
                row.active(),
                monthStatus);
    }

    private static LocalDate resolvePaymentDate(
            LocalDate requestedPaymentDate, YearMonth targetMonth, int dueDay, LocalDate today) {
        if (requestedPaymentDate != null) {
            if (!YearMonth.from(requestedPaymentDate).equals(targetMonth)) {
                throw new BusinessRuleException(
                        "Payment date must be within target month " + targetMonth);
            }
            return requestedPaymentDate;
        }
        if (YearMonth.from(today).equals(targetMonth)) {
            return today;
        }
        return RecurringSchedule.dueDateForMonth(dueDay, targetMonth);
    }

    private static void requireAdult(CurrentUser user) {
        if (!user.isAdult()) {
            throw new ForbiddenException("Only adult users can manage recurring expenses");
        }
    }

    private static void requireCanManage(CurrentUser user, RecurringExpense entity) {
        if (entity.getScope() == Scope.PERSONAL && !entity.isCreatedBy(user.userId())) {
            throw new ForbiddenException(
                    "You do not have permission to manage another member's personal recurring expense");
        }
    }

    private static void requireCanConfirmPayment(CurrentUser user, RecurringExpense entity) {
        if (entity.getScope() == Scope.PERSONAL && !entity.isCreatedBy(user.userId())) {
            throw new ForbiddenException(
                    "Only the owner can confirm payments for a personal recurring expense");
        }
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRecurringExpenseRequestException(
                    "name", "Recurring expense name must not be blank");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidRecurringExpenseRequestException(
                    "name", "Recurring expense name must be at most 120 characters");
        }
        return trimmed;
    }

    private static BigDecimal validatePositiveAmount(String field, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRecurringExpenseRequestException(
                    field, "Amount must be greater than zero");
        }
        BigDecimal scaled = Money.scale(amount);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRecurringExpenseRequestException(
                    field, "Amount must be greater than zero");
        }
        return scaled;
    }

    private static String validateCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new InvalidRecurringExpenseRequestException(
                    "currencyCode", "Currency code is required");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private static int validateDueDay(Integer dueDay) {
        if (dueDay == null || dueDay < 1 || dueDay > 31) {
            throw new InvalidRecurringExpenseRequestException(
                    "dueDay", "Due day must be between 1 and 31");
        }
        return dueDay;
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessRuleException("End date must be on or after start date");
        }
    }

    private static void requireEnabledCurrency(FamilySettings settings, String currencyCode) {
        boolean enabled = settings.enabledCurrencies() != null
                && settings.enabledCurrencies().stream()
                        .anyMatch(c -> currencyCode.equalsIgnoreCase(c));
        if (!enabled) {
            throw new BusinessRuleException(
                    "Currency is not enabled for this family: " + currencyCode);
        }
    }

    private static void validateAccountCurrency(AccountRef account, String currencyCode) {
        if (CREDIT_CARD_ACCOUNT_TYPE.equals(account.accountType())) {
            return;
        }
        if (!currencyCode.equalsIgnoreCase(account.currencyCode())) {
            throw new BusinessRuleException(
                    "Recurring expense currency "
                            + currencyCode
                            + " does not match account currency "
                            + account.currencyCode());
        }
    }
}
