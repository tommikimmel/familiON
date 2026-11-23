package com.familion.finance.accounts.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ConflictException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.events.FamilyFinancialDataChangedEvent;
import com.familion.common.money.Money;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.View;
import com.familion.common.security.ViewAccess;
import com.familion.common.security.VisibilityPolicy;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.accounts.domain.Account;
import com.familion.finance.accounts.domain.AccountType;
import com.familion.finance.accounts.infrastructure.persistence.AccountRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountBalanceQuery accountBalanceQuery;
    private final VisibilityPolicy visibilityPolicy;
    private final FamilyQueryApi familyQueryApi;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AccountService(
            AccountRepository accountRepository,
            AccountBalanceQuery accountBalanceQuery,
            VisibilityPolicy visibilityPolicy,
            FamilyQueryApi familyQueryApi,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.accountBalanceQuery = accountBalanceQuery;
        this.visibilityPolicy = visibilityPolicy;
        this.familyQueryApi = familyQueryApi;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AccountView> listAccounts(
            CurrentUser currentUser, View view, boolean includeInactive) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        View effectiveView = view == null ? View.PERSONAL : view;

        ViewAccess access = visibilityPolicy.resolveForListing(currentUser, effectiveView);
        AccountListingScope scope;
        if (access == ViewAccess.FAMILY_FULL) {
            scope = AccountListingScope.ALL_FAMILY;
        } else {
            scope = currentUser.isAdult()
                    ? AccountListingScope.OWN_AND_SHARED
                    : AccountListingScope.OWN_ONLY;
        }

        return accountBalanceQuery.listWithBalances(
                currentUser.familyId(), currentUser.userId(), scope, includeInactive);
    }

    @Transactional
    public AccountView createAccount(CurrentUser currentUser, CreateAccountCommand command) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(command, "command must not be null");

        String trimmedName = requireNonBlankName(command.name());
        String currencyCode = command.currencyCode().trim().toUpperCase(Locale.ROOT);
        UUID familyId = currentUser.familyId();

        FamilySettings settings = familyQueryApi.getSettings(familyId);
        if (!settings.enabledCurrencies().contains(currencyCode)) {
            throw new BusinessRuleException(
                    "Currency is not enabled for this family: " + currencyCode);
        }

        UUID ownerUserId;
        if (currentUser.isAdult()) {
            ownerUserId = command.ownerUserId();
            if (ownerUserId != null) {
                familyQueryApi.requireMember(familyId, ownerUserId);
            }
        } else {
            ownerUserId = currentUser.userId();
        }

        CreditCardFields cardFields = resolveCreditCardFields(
                command.accountType(),
                command.creditLimit(),
                command.closingDay(),
                command.dueDay());

        if (accountRepository.existsByFamilyIdAndNameIgnoreCaseAndActiveTrueAndDeletedAtIsNull(
                familyId, trimmedName)) {
            throw new ConflictException(
                    "An active account with this name already exists in the family");
        }

        BigDecimal initialBalance = command.initialBalance() == null
                ? Money.scale(BigDecimal.ZERO)
                : Money.scale(command.initialBalance());

        Account account = Account.create(
                familyId,
                ownerUserId,
                trimmedName,
                command.accountType(),
                currencyCode,
                initialBalance,
                cardFields.creditLimit(),
                cardFields.closingDay(),
                cardFields.dueDay());
        Account saved = accountRepository.saveAndFlush(account);
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));

        return AccountView.from(saved, saved.getInitialBalance());
    }

    @Transactional
    public AccountView updateAccount(
            CurrentUser currentUser, UUID accountId, UpdateAccountCommand command) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        UUID familyId = currentUser.familyId();
        Account account = accountRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(accountId, familyId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        requireCanManage(currentUser, account);

        String effectiveName = account.getName();
        if (command.name() != null) {
            effectiveName = requireNonBlankName(command.name());
            if (!effectiveName.equalsIgnoreCase(account.getName())
                    && account.isActive()
                    && accountRepository
                            .existsByFamilyIdAndNameIgnoreCaseAndActiveTrueAndDeletedAtIsNullAndIdNot(
                                    familyId, effectiveName, accountId)) {
                throw new ConflictException(
                        "An active account with this name already exists in the family");
            }
        }

        UUID effectiveOwner = account.getOwnerUserId();
        if (command.ownerUserIdPresent() && currentUser.isAdult()) {
            effectiveOwner = command.ownerUserId();
            if (effectiveOwner != null) {
                familyQueryApi.requireMember(familyId, effectiveOwner);
            }
        }

        AccountType effectiveType =
                command.accountType() != null ? command.accountType() : account.getAccountType();

        BigDecimal requestedCreditLimit =
                command.creditLimitPresent() ? command.creditLimit() : account.getCreditLimit();
        Integer requestedClosingDay =
                command.closingDay() != null ? command.closingDay() : account.getClosingDay();
        Integer requestedDueDay =
                command.dueDay() != null ? command.dueDay() : account.getDueDay();

        CreditCardFields cardFields = resolveCreditCardFields(
                effectiveType, requestedCreditLimit, requestedClosingDay, requestedDueDay);

        BigDecimal effectiveInitialBalance = command.initialBalance() != null
                ? Money.scale(command.initialBalance())
                : account.getInitialBalance();

        account.update(
                effectiveName,
                effectiveType,
                effectiveInitialBalance,
                effectiveOwner,
                cardFields.creditLimit(),
                cardFields.closingDay(),
                cardFields.dueDay());
        Account saved = accountRepository.saveAndFlush(account);
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));

        BigDecimal currentBalance = accountBalanceQuery
                .computeCurrentBalance(saved.getId())
                .orElse(saved.getInitialBalance());
        return AccountView.from(saved, currentBalance);
    }

    @Transactional
    public void deleteAccount(CurrentUser currentUser, UUID accountId) {
        Objects.requireNonNull(currentUser, "currentUser must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");

        UUID familyId = currentUser.familyId();
        Account account = accountRepository
                .findByIdAndFamilyIdAndDeletedAtIsNull(accountId, familyId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        requireCanManage(currentUser, account);

        account.softDelete(Instant.now(clock));
        accountRepository.saveAndFlush(account);
        eventPublisher.publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    private static void requireCanManage(CurrentUser currentUser, Account account) {
        if (!currentUser.isAdult() && !account.isOwnedBy(currentUser.userId())) {
            throw new ForbiddenException("You do not have permission to manage this account");
        }
    }

    private static String requireNonBlankName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidAccountRequestException("name", "Account name must not be blank");
        }
        return name.trim();
    }

    private static CreditCardFields resolveCreditCardFields(
            AccountType accountType,
            BigDecimal creditLimit,
            Integer closingDay,
            Integer dueDay) {
        if (accountType != AccountType.CREDIT_CARD) {
            return new CreditCardFields(null, null, null);
        }
        if (closingDay == null || closingDay < 1 || closingDay > 31) {
            throw new InvalidAccountRequestException(
                    "closingDay",
                    "Closing day is required and must be between 1 and 31 for credit cards");
        }
        if (dueDay == null || dueDay < 1 || dueDay > 31) {
            throw new InvalidAccountRequestException(
                    "dueDay",
                    "Due day is required and must be between 1 and 31 for credit cards");
        }
        if (creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAccountRequestException(
                    "creditLimit", "Credit limit must be zero or greater");
        }
        return new CreditCardFields(
                creditLimit == null ? null : Money.scale(creditLimit), closingDay, dueDay);
    }

    private record CreditCardFields(
            BigDecimal creditLimit, Integer closingDay, Integer dueDay) {}
}
