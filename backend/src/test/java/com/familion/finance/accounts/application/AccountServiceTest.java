package com.familion.finance.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ConflictException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.events.FamilyFinancialDataChangedEvent;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.DefaultVisibilityPolicy;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.security.View;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.accounts.domain.Account;
import com.familion.finance.accounts.domain.AccountType;
import com.familion.finance.accounts.infrastructure.persistence.AccountRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AccountServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-15T12:00:00Z");

    private AccountRepository accountRepository;
    private AccountBalanceQuery accountBalanceQuery;
    private FamilyQueryApi familyQueryApi;
    private ApplicationEventPublisher eventPublisher;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        accountBalanceQuery = mock(AccountBalanceQuery.class);
        familyQueryApi = mock(FamilyQueryApi.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        accountService = new AccountService(
                accountRepository,
                accountBalanceQuery,
                new DefaultVisibilityPolicy(),
                familyQueryApi,
                eventPublisher,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    void listAccountsAppliesPersonalAndFamilyVisibilityRules() {
        UUID familyId = UUID.randomUUID();
        CurrentUser adult = new CurrentUser(UUID.randomUUID(), familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        CurrentUser childOwn = new CurrentUser(UUID.randomUUID(), familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        CurrentUser childFull = new CurrentUser(UUID.randomUUID(), familyId, Role.HIJO, FamilyVisibility.FAMILY_FULL);

        when(accountBalanceQuery.listWithBalances(any(), any(), any(), eq(false))).thenReturn(List.of());

        accountService.listAccounts(adult, View.PERSONAL, false);
        verify(accountBalanceQuery)
                .listWithBalances(familyId, adult.userId(), AccountListingScope.OWN_AND_SHARED, false);

        accountService.listAccounts(childOwn, View.PERSONAL, false);
        verify(accountBalanceQuery)
                .listWithBalances(familyId, childOwn.userId(), AccountListingScope.OWN_ONLY, false);

        accountService.listAccounts(childFull, View.FAMILY, false);
        verify(accountBalanceQuery)
                .listWithBalances(familyId, childFull.userId(), AccountListingScope.ALL_FAMILY, false);

        assertThatThrownBy(() -> accountService.listAccounts(childOwn, View.FAMILY, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createAccountRejectsDisabledCurrencyWith422() {
        UUID familyId = UUID.randomUUID();
        CurrentUser adult = new CurrentUser(UUID.randomUUID(), familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        stubFamilySettings(familyId, List.of("ARS", "USD"));

        CreateAccountCommand cmd = new CreateAccountCommand(
                "Euro Wallet", AccountType.WALLET, "EUR", BigDecimal.ZERO, null, null, null, null);

        assertThatThrownBy(() -> accountService.createAccount(adult, cmd))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("EUR");
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void createAccountRequiresClosingAndDueDaysForCreditCardsAndIgnoresThemForOtherTypes() {
        UUID familyId = UUID.randomUUID();
        CurrentUser adult = new CurrentUser(UUID.randomUUID(), familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        stubFamilySettings(familyId, List.of("ARS"));

        CreateAccountCommand missingClosing = new CreateAccountCommand(
                "Visa", AccountType.CREDIT_CARD, "ARS", BigDecimal.ZERO, null, new BigDecimal("500000"), null, 10);
        assertThatThrownBy(() -> accountService.createAccount(adult, missingClosing))
                .isInstanceOf(InvalidAccountRequestException.class);

        CreateAccountCommand invalidDue = new CreateAccountCommand(
                "Visa", AccountType.CREDIT_CARD, "ARS", BigDecimal.ZERO, null, null, 20, 32);
        assertThatThrownBy(() -> accountService.createAccount(adult, invalidDue))
                .isInstanceOf(InvalidAccountRequestException.class);

        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            setField(a, "id", UUID.randomUUID());
            return a;
        });

        CreateAccountCommand cashWithCardFields = new CreateAccountCommand(
                "Efectivo", AccountType.CASH, "ARS", new BigDecimal("1000"), null, new BigDecimal("999"), 15, 25);
        AccountView created = accountService.createAccount(adult, cashWithCardFields);

        assertThat(created.closingDay()).isNull();
        assertThat(created.dueDay()).isNull();
        assertThat(created.creditLimit()).isNull();
        assertThat(created.initialBalance()).isEqualByComparingTo("1000.0000");
        assertThat(created.currentBalance()).isEqualByComparingTo("1000.0000");
        verify(eventPublisher).publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    @Test
    void createAccountForcesChildOwnershipToSelfAndValidatesAdultMemberOwnership() {
        UUID familyId = UUID.randomUUID();
        UUID adultId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        CurrentUser child = new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        CurrentUser adult = new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        stubFamilySettings(familyId, List.of("ARS"));
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            setField(a, "id", UUID.randomUUID());
            return a;
        });

        AccountView childAccount = accountService.createAccount(
                child,
                new CreateAccountCommand("Mi Billetera", AccountType.WALLET, "ARS", null, adultId, null, null, null));
        assertThat(childAccount.ownerUserId()).isEqualTo(childId);
        verify(familyQueryApi, never()).requireMember(any(), any());

        when(familyQueryApi.requireMember(familyId, childId))
                .thenReturn(new MemberRef(childId, familyId, "Hijo", Role.HIJO, "#0F766E"));
        AccountView adultForChild = accountService.createAccount(
                adult,
                new CreateAccountCommand("Ahorro Hijo", AccountType.SAVINGS, "ARS", null, childId, null, null, null));
        assertThat(adultForChild.ownerUserId()).isEqualTo(childId);
        verify(familyQueryApi).requireMember(familyId, childId);
    }

    @Test
    void createAccountRejectsDuplicateActiveNameWith409() {
        UUID familyId = UUID.randomUUID();
        CurrentUser adult = new CurrentUser(UUID.randomUUID(), familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        stubFamilySettings(familyId, List.of("ARS"));
        when(accountRepository.existsByFamilyIdAndNameIgnoreCaseAndActiveTrueAndDeletedAtIsNull(familyId, "Banco"))
                .thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(
                        adult,
                        new CreateAccountCommand("  Banco ", AccountType.BANK, "ARS", null, null, null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateAndDeleteForbidChildManagingSharedOrOthersAccount() {
        UUID familyId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        CurrentUser child = new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        Account shared = Account.create(
                familyId, null, "Caja Común", AccountType.CASH, "ARS", BigDecimal.ZERO, null, null, null);
        setField(shared, "id", accountId);
        when(accountRepository.findByIdAndFamilyIdAndDeletedAtIsNull(accountId, familyId))
                .thenReturn(Optional.of(shared));

        assertThatThrownBy(() -> accountService.updateAccount(
                        child,
                        accountId,
                        new UpdateAccountCommand("Robada", null, null, false, null, false, null, null, null)))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> accountService.deleteAccount(child, accountId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteAccountSoftDeletesAndPublishesEvent() {
        UUID familyId = UUID.randomUUID();
        UUID adultId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        CurrentUser adult = new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        Account account = Account.create(
                familyId, adultId, "Vieja", AccountType.BANK, "ARS", BigDecimal.ZERO, null, null, null);
        setField(account, "id", accountId);
        when(accountRepository.findByIdAndFamilyIdAndDeletedAtIsNull(accountId, familyId))
                .thenReturn(Optional.of(account));

        accountService.deleteAccount(adult, accountId);

        assertThat(account.isActive()).isFalse();
        assertThat(account.getDeletedAt()).isEqualTo(FIXED_NOW);
        verify(accountRepository).saveAndFlush(account);
        verify(eventPublisher).publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    private void stubFamilySettings(UUID familyId, List<String> enabledCurrencies) {
        when(familyQueryApi.getSettings(familyId))
                .thenReturn(new FamilySettings(
                        familyId,
                        "Familia",
                        "ARS",
                        "USD",
                        "USD_MEP",
                        ZoneId.of("America/Argentina/Buenos_Aires"),
                        enabledCurrencies));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
