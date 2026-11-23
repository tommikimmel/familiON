package com.familion.finance.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.finance.accounts.application.api.AccountRef;
import com.familion.finance.accounts.domain.Account;
import com.familion.finance.accounts.domain.AccountType;
import com.familion.finance.accounts.infrastructure.persistence.AccountRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountQueryServiceTest {

    private AccountRepository accountRepository;
    private AccountBalanceQuery accountBalanceQuery;
    private AccountQueryService queryService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        accountBalanceQuery = mock(AccountBalanceQuery.class);
        queryService = new AccountQueryService(accountRepository, accountBalanceQuery);
    }

    @Test
    void requireUsableThrows404ForOtherFamilyOrDeletedAccount() {
        UUID familyId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        CurrentUser adult = new CurrentUser(UUID.randomUUID(), familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        when(accountRepository.findByIdAndFamilyId(accountId, familyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> queryService.requireUsable(adult, accountId))
                .isInstanceOf(NotFoundException.class);

        Account deleted = Account.create(
                familyId, adult.userId(), "Closed", AccountType.CASH, "ARS", BigDecimal.ZERO, null, null, null);
        setField(deleted, "id", accountId);
        deleted.softDelete(Instant.now());
        when(accountRepository.findByIdAndFamilyId(accountId, familyId)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> queryService.requireUsable(adult, accountId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requireUsableThrows403WhenChildAccessesSharedOrOthersAccountAndSucceedsForOwn() {
        UUID familyId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID sharedId = UUID.randomUUID();
        UUID ownId = UUID.randomUUID();
        CurrentUser child = new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.FAMILY_FULL);

        Account shared = Account.create(
                familyId, null, "Shared", AccountType.BANK, "ARS", BigDecimal.ZERO, null, null, null);
        setField(shared, "id", sharedId);
        when(accountRepository.findByIdAndFamilyId(sharedId, familyId)).thenReturn(Optional.of(shared));

        assertThatThrownBy(() -> queryService.requireUsable(child, sharedId))
                .isInstanceOf(ForbiddenException.class);

        Account own = Account.create(
                familyId, childId, "Own Card", AccountType.CREDIT_CARD, "ARS", BigDecimal.ZERO, null, 20, 5);
        setField(own, "id", ownId);
        when(accountRepository.findByIdAndFamilyId(ownId, familyId)).thenReturn(Optional.of(own));

        AccountRef ref = queryService.requireUsable(child, ownId);
        assertThat(ref).isEqualTo(
                new AccountRef(ownId, familyId, childId, "Own Card", "CREDIT_CARD", "ARS", 20, 5));
    }

    @Test
    void currentBalanceReturnsComputedBalanceOr404WhenAccountMissing() {
        UUID accountId = UUID.randomUUID();
        when(accountBalanceQuery.computeCurrentBalance(accountId))
                .thenReturn(Optional.of(new BigDecimal("-12500.5000")));
        assertThat(queryService.currentBalance(accountId)).isEqualByComparingTo("-12500.5000");

        UUID missingId = UUID.randomUUID();
        when(accountBalanceQuery.computeCurrentBalance(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> queryService.currentBalance(missingId))
                .isInstanceOf(NotFoundException.class);
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
