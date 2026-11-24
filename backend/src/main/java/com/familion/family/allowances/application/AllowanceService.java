package com.familion.family.allowances.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.events.FamilyFinancialDataChangedEvent;
import com.familion.common.money.Money;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.Role;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.allowances.application.api.AllowanceDisbursedEvent;
import com.familion.family.allowances.domain.Allowance;
import com.familion.family.allowances.domain.AllowanceAdjustmentRule;
import com.familion.family.allowances.domain.AllowanceAmountCalculator;
import com.familion.family.allowances.domain.AllowanceFrequency;
import com.familion.family.allowances.domain.AllowanceSchedule;
import com.familion.family.allowances.infrastructure.persistence.AllowanceRepository;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.accounts.application.api.AccountRef;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.categories.application.api.CategoryRef;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AllowanceService {

    private static final String ALLOWANCE_INCOME_SYSTEM_KEY = "ALLOWANCE_INCOME";
    private static final String CREDIT_CARD_ACCOUNT_TYPE = "CREDIT_CARD";

    private final AllowanceRepository allowanceRepository;
    private final AllowanceReadQuery readQuery;
    private final FamilyQueryApi familyQueryApi;
    private final AccountQueryApi accountQueryApi;
    private final CategoryQueryApi categoryQueryApi;
    private final EconomicsApi economicsApi;
    private final TransactionCommandApi transactionCommandApi;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final AllowanceAmountCalculator amountCalculator;

    public AllowanceService(
            AllowanceRepository allowanceRepository,
            AllowanceReadQuery readQuery,
            FamilyQueryApi familyQueryApi,
            AccountQueryApi accountQueryApi,
            CategoryQueryApi categoryQueryApi,
            EconomicsApi economicsApi,
            TransactionCommandApi transactionCommandApi,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.allowanceRepository = allowanceRepository;
        this.readQuery = readQuery;
        this.familyQueryApi = familyQueryApi;
        this.accountQueryApi = accountQueryApi;
        this.categoryQueryApi = categoryQueryApi;
        this.economicsApi = economicsApi;
        this.transactionCommandApi = transactionCommandApi;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.amountCalculator = new AllowanceAmountCalculator();
    }

    @Transactional(readOnly = true)
    public List<AllowanceView> list(CurrentUser currentUser, boolean includeInactive) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");

        FamilySettings settings = familyQueryApi.getSettings(currentUser.familyId());
        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));

        List<AllowanceReadRow> rows = readQuery.findVisibleForUser(
                currentUser.familyId(), currentUser.userId(), currentUser.isAdult(), includeInactive);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> allowanceIds = rows.stream().map(AllowanceReadRow::id).toList();
        Map<UUID, LocalDate> lastDisbursementDates =
                readQuery.findLastDisbursementDates(allowanceIds);
        Map<UUID, BigDecimal> balanceByAccount = new HashMap<>();

        List<AllowanceView> result = new ArrayList<>(rows.size());
        for (AllowanceReadRow row : rows) {
            BigDecimal childBalance = balanceByAccount.computeIfAbsent(
                    row.targetAccountId(), accountQueryApi::currentBalance);
            result.add(toView(
                    row, lastDisbursementDates.get(row.id()), childBalance, today, settings));
        }
        return List.copyOf(result);
    }

    @Transactional
    public AllowanceView create(CurrentUser currentUser, CreateAllowanceCommand command) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(command, "command must not be null");
        requireAdult(currentUser);

        UUID childUserId = requireUuid("childUserId", command.childUserId());
        UUID targetAccountId = requireUuid("targetAccountId", command.targetAccountId());
        BigDecimal scaledAmount = validatePositiveAmount("amount", command.amount());
        String currencyCode = validateCurrency(command.currencyCode());
        AllowanceFrequency frequency =
                requireNonNullField("frequency", command.frequency(), "Frequency is required");
        int dayOfPeriod = validateDayOfPeriodField(command.dayOfPeriod());
        AllowanceSchedule.validateDayOfPeriod(frequency, dayOfPeriod);
        AllowanceAdjustmentRule adjustmentRule = requireNonNullField(
                "adjustmentRule", command.adjustmentRule(), "Adjustment rule is required");

        UUID familyId = currentUser.familyId();
        FamilySettings settings = familyQueryApi.getSettings(familyId);
        requireEnabledCurrency(settings, currencyCode);

        MemberRef child = requireChildMember(familyId, childUserId);
        MemberRef assignedBy = familyQueryApi.requireMember(familyId, currentUser.userId());
        AccountRef targetAccount =
                requireChildTargetAccount(currentUser, targetAccountId, child.userId(), currencyCode);

        AccountRef sourceAccount = null;
        if (command.sourceAccountId() != null) {
            sourceAccount = requireFamilySourceAccount(
                    currentUser, command.sourceAccountId(), targetAccount.id(), currencyCode);
        }

        Instant createdAt = Instant.now(clock);
        Allowance entity = Allowance.create(
                familyId,
                currentUser.userId(),
                child.userId(),
                sourceAccount == null ? null : sourceAccount.id(),
                targetAccount.id(),
                scaledAmount,
                currencyCode,
                frequency,
                dayOfPeriod,
                adjustmentRule,
                createdAt);
        Allowance saved = allowanceRepository.saveAndFlush(entity);
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));

        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        BigDecimal childBalance = accountQueryApi.currentBalance(targetAccount.id());

        AllowanceReadRow row = new AllowanceReadRow(
                saved.getId(),
                saved.getFamilyId(),
                child.userId(),
                child.fullName(),
                child.accentColor(),
                assignedBy.userId(),
                assignedBy.fullName(),
                sourceAccount == null ? null : sourceAccount.id(),
                sourceAccount == null ? null : sourceAccount.name(),
                targetAccount.id(),
                targetAccount.name(),
                saved.getAmount(),
                saved.getCurrencyCode(),
                saved.getFrequency(),
                saved.getDayOfPeriod(),
                saved.getAdjustmentRule(),
                saved.isActive(),
                saved.getCreatedAt());

        return toView(row, null, childBalance, today, settings);
    }

    @Transactional
    public AllowanceView update(
            CurrentUser currentUser, UUID allowanceId, UpdateAllowanceCommand command) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(allowanceId, "allowanceId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        requireAdult(currentUser);

        UUID familyId = currentUser.familyId();
        Allowance entity = allowanceRepository
                .findByIdAndFamilyId(allowanceId, familyId)
                .orElseThrow(() -> new NotFoundException("Allowance not found"));

        UUID effectiveChildUserId =
                command.childUserId() != null ? command.childUserId() : entity.getChildUserId();
        UUID effectiveSourceAccountId = command.sourceAccountIdPresent()
                ? command.sourceAccountId()
                : entity.getSourceAccountId();
        UUID effectiveTargetAccountId = command.targetAccountId() != null
                ? command.targetAccountId()
                : entity.getTargetAccountId();
        BigDecimal effectiveAmount = command.amount() != null
                ? validatePositiveAmount("amount", command.amount())
                : entity.getAmount();
        String effectiveCurrencyCode = command.currencyCode() != null
                ? validateCurrency(command.currencyCode())
                : entity.getCurrencyCode();
        AllowanceFrequency effectiveFrequency =
                command.frequency() != null ? command.frequency() : entity.getFrequency();
        int effectiveDayOfPeriod = command.dayOfPeriod() != null
                ? validateDayOfPeriodField(command.dayOfPeriod())
                : entity.getDayOfPeriod();
        AllowanceAdjustmentRule effectiveAdjustmentRule = command.adjustmentRule() != null
                ? command.adjustmentRule()
                : entity.getAdjustmentRule();
        boolean effectiveActive = command.active() != null ? command.active() : entity.isActive();

        AllowanceSchedule.validateDayOfPeriod(effectiveFrequency, effectiveDayOfPeriod);

        FamilySettings settings = familyQueryApi.getSettings(familyId);
        if (command.currencyCode() != null) {
            requireEnabledCurrency(settings, effectiveCurrencyCode);
        }

        if (command.childUserId() != null) {
            requireChildMember(familyId, effectiveChildUserId);
        }

        boolean shouldValidateTarget = command.childUserId() != null
                || command.targetAccountId() != null
                || command.currencyCode() != null;
        if (shouldValidateTarget) {
            requireChildTargetAccount(
                    currentUser,
                    effectiveTargetAccountId,
                    effectiveChildUserId,
                    effectiveCurrencyCode);
        }

        boolean shouldValidateSource = effectiveSourceAccountId != null
                && (command.sourceAccountIdPresent()
                        || command.targetAccountId() != null
                        || command.currencyCode() != null);
        if (shouldValidateSource) {
            requireFamilySourceAccount(
                    currentUser,
                    effectiveSourceAccountId,
                    effectiveTargetAccountId,
                    effectiveCurrencyCode);
        }

        entity.update(
                effectiveChildUserId,
                effectiveSourceAccountId,
                effectiveTargetAccountId,
                effectiveAmount,
                effectiveCurrencyCode,
                effectiveFrequency,
                effectiveDayOfPeriod,
                effectiveAdjustmentRule,
                effectiveActive);
        allowanceRepository.saveAndFlush(entity);
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));

        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        return loadView(familyId, entity.getId(), today, settings);
    }

    @Transactional
    public void deactivate(CurrentUser currentUser, UUID allowanceId) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(allowanceId, "allowanceId must not be null");
        requireAdult(currentUser);

        UUID familyId = currentUser.familyId();
        Allowance entity = allowanceRepository
                .findByIdAndFamilyId(allowanceId, familyId)
                .orElseThrow(() -> new NotFoundException("Allowance not found"));

        entity.deactivate();
        allowanceRepository.saveAndFlush(entity);
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    @Transactional
    public DisbursementResult disburse(
            CurrentUser currentUser, UUID allowanceId, DisburseAllowanceCommand command) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(allowanceId, "allowanceId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        requireAdult(currentUser);

        UUID familyId = currentUser.familyId();
        Allowance entity = allowanceRepository
                .findByIdAndFamilyIdForUpdate(allowanceId, familyId)
                .orElseThrow(() -> new NotFoundException("Allowance not found"));

        if (!entity.isActive()) {
            throw new BusinessRuleException("Cannot disburse an inactive allowance");
        }

        FamilySettings settings = familyQueryApi.getSettings(familyId);
        LocalDate today = LocalDate.now(clock.withZone(settings.timezone()));
        LocalDate disbursementDate = command.date() != null ? command.date() : today;

        MemberRef child = requireChildMember(familyId, entity.getChildUserId());
        AccountRef targetAccount = requireChildTargetAccount(
                currentUser, entity.getTargetAccountId(), child.userId(), entity.getCurrencyCode());

        AccountRef sourceAccount = null;
        if (entity.getSourceAccountId() != null) {
            sourceAccount = requireFamilySourceAccount(
                    currentUser,
                    entity.getSourceAccountId(),
                    targetAccount.id(),
                    entity.getCurrencyCode());
        }

        BigDecimal disbursementAmount;
        if (command.amount() != null) {
            disbursementAmount = validatePositiveAmount("amount", command.amount());
        } else {
            LocalDate createdDate = entity.getCreatedAt().atZone(settings.timezone()).toLocalDate();
            disbursementAmount = amountCalculator.calculateCurrentAmount(
                    entity.getAdjustmentRule(),
                    entity.getAmount(),
                    entity.getCurrencyCode(),
                    createdDate,
                    today,
                    settings.referenceCurrency(),
                    settings.referenceRateSource(),
                    economicsApi);
        }

        UUID transferGroupId = UUID.randomUUID();
        String description = "Mensualidad · " + child.fullName();

        UUID outTransactionId = null;
        if (sourceAccount != null) {
            outTransactionId = transactionCommandApi.record(new NewTransactionCommand(
                    familyId,
                    currentUser.userId(),
                    currentUser.userId(),
                    sourceAccount.id(),
                    null,
                    TransactionType.ALLOWANCE_OUT,
                    Scope.PERSONAL,
                    TransactionStatus.POSTED,
                    disbursementAmount,
                    entity.getCurrencyCode(),
                    disbursementDate,
                    description,
                    null,
                    null,
                    null,
                    null,
                    null,
                    entity.getId(),
                    null,
                    transferGroupId));
        }

        CategoryRef allowanceIncomeCategory =
                categoryQueryApi.requireSystemCategory(ALLOWANCE_INCOME_SYSTEM_KEY);
        UUID inTransactionId = transactionCommandApi.record(new NewTransactionCommand(
                familyId,
                currentUser.userId(),
                child.userId(),
                targetAccount.id(),
                allowanceIncomeCategory.id(),
                TransactionType.ALLOWANCE_IN,
                Scope.PERSONAL,
                TransactionStatus.POSTED,
                disbursementAmount,
                entity.getCurrencyCode(),
                disbursementDate,
                description,
                null,
                null,
                null,
                null,
                null,
                entity.getId(),
                null,
                transferGroupId));

        eventPublisher.publishEvent(new AllowanceDisbursedEvent(
                familyId,
                entity.getId(),
                child.userId(),
                disbursementAmount,
                entity.getCurrencyCode()));

        AllowanceView updatedView = loadView(familyId, entity.getId(), today, settings);
        return new DisbursementResult(updatedView, outTransactionId, inTransactionId);
    }

    private AllowanceView loadView(
            UUID familyId, UUID allowanceId, LocalDate today, FamilySettings settings) {
        AllowanceReadRow row = readQuery
                .findById(familyId, allowanceId)
                .orElseThrow(() -> new NotFoundException("Allowance not found"));
        LocalDate lastDisbursementDate =
                readQuery.findLastDisbursementDates(List.of(allowanceId)).get(allowanceId);
        BigDecimal childBalance = accountQueryApi.currentBalance(row.targetAccountId());
        return toView(row, lastDisbursementDate, childBalance, today, settings);
    }

    private AllowanceView toView(
            AllowanceReadRow row,
            LocalDate lastDisbursementDate,
            BigDecimal childBalance,
            LocalDate today,
            FamilySettings settings) {
        LocalDate createdDate = row.createdAt().atZone(settings.timezone()).toLocalDate();
        BigDecimal currentAmount = amountCalculator.calculateCurrentAmount(
                row.adjustmentRule(),
                row.amount(),
                row.currencyCode(),
                createdDate,
                today,
                settings.referenceCurrency(),
                settings.referenceRateSource(),
                economicsApi);

        LocalDate nextDisbursementDate = AllowanceSchedule.nextDisbursementDate(
                row.frequency(), row.dayOfPeriod(), lastDisbursementDate, today);

        AllowanceView.ChildSummary childSummary = new AllowanceView.ChildSummary(
                row.childUserId(), row.childFullName(), row.childAccentColor());
        AllowanceView.AssignedBySummary assignedBySummary = new AllowanceView.AssignedBySummary(
                row.assignedByUserId(), row.assignedByFullName());
        AllowanceView.AccountSummary sourceAccountSummary = row.sourceAccountId() == null
                ? null
                : new AllowanceView.AccountSummary(row.sourceAccountId(), row.sourceAccountName());
        AllowanceView.AccountSummary targetAccountSummary =
                new AllowanceView.AccountSummary(row.targetAccountId(), row.targetAccountName());

        return new AllowanceView(
                row.id(),
                childSummary,
                assignedBySummary,
                sourceAccountSummary,
                targetAccountSummary,
                row.amount(),
                row.currencyCode(),
                row.frequency(),
                row.dayOfPeriod(),
                row.adjustmentRule(),
                row.active(),
                currentAmount,
                nextDisbursementDate,
                lastDisbursementDate,
                Money.scale(childBalance));
    }

    private MemberRef requireChildMember(UUID familyId, UUID childUserId) {
        MemberRef member;
        try {
            member = familyQueryApi.requireMember(familyId, childUserId);
        } catch (NotFoundException ex) {
            throw new BusinessRuleException("Child user must be an active member of the family");
        }
        if (member.role() != Role.HIJO) {
            throw new BusinessRuleException("Allowance recipient must have role HIJO");
        }
        return member;
    }

    private AccountRef requireChildTargetAccount(
            CurrentUser currentUser, UUID targetAccountId, UUID childUserId, String currencyCode) {
        AccountRef account;
        try {
            account = accountQueryApi.requireUsable(currentUser, targetAccountId);
        } catch (NotFoundException ex) {
            throw new BusinessRuleException(
                    "Target account must be an active account owned by the child");
        }
        if (account.ownerUserId() == null || !account.ownerUserId().equals(childUserId)) {
            throw new BusinessRuleException("Target account must belong to the child");
        }
        validateAccountCurrency(account, currencyCode);
        return account;
    }

    private AccountRef requireFamilySourceAccount(
            CurrentUser currentUser,
            UUID sourceAccountId,
            UUID targetAccountId,
            String currencyCode) {
        if (sourceAccountId.equals(targetAccountId)) {
            throw new BusinessRuleException("Source and target accounts must be different");
        }
        AccountRef account;
        try {
            account = accountQueryApi.requireUsable(currentUser, sourceAccountId);
        } catch (NotFoundException ex) {
            throw new BusinessRuleException(
                    "Source account must be an active account in the family");
        }
        validateAccountCurrency(account, currencyCode);
        return account;
    }

    private static void requireAdult(CurrentUser user) {
        if (!user.isAdult()) {
            throw new ForbiddenException("Only adult users can manage allowances");
        }
    }

    private static UUID requireUuid(String field, UUID value) {
        if (value == null) {
            throw new InvalidAllowanceRequestException(field, field + " is required");
        }
        return value;
    }

    private static <T> T requireNonNullField(String field, T value, String message) {
        if (value == null) {
            throw new InvalidAllowanceRequestException(field, message);
        }
        return value;
    }

    private static BigDecimal validatePositiveAmount(String field, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAllowanceRequestException(field, "Amount must be greater than zero");
        }
        BigDecimal scaled = Money.scale(amount);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAllowanceRequestException(field, "Amount must be greater than zero");
        }
        return scaled;
    }

    private static String validateCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new InvalidAllowanceRequestException(
                    "currencyCode", "Currency code is required");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private static int validateDayOfPeriodField(Integer dayOfPeriod) {
        if (dayOfPeriod == null || dayOfPeriod < 1 || dayOfPeriod > 31) {
            throw new InvalidAllowanceRequestException(
                    "dayOfPeriod", "Day of period must be between 1 and 31");
        }
        return dayOfPeriod;
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
                    "Allowance currency "
                            + currencyCode
                            + " does not match account currency "
                            + account.currencyCode());
        }
    }
}
