package com.familion.family.allowances.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.allowances.application.api.AllowanceDisbursedEvent;
import com.familion.family.allowances.domain.Allowance;
import com.familion.family.allowances.domain.AllowanceAdjustmentRule;
import com.familion.family.allowances.domain.AllowanceFrequency;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class AllowanceServiceTest {

    private static final ZoneId AR_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant FIXED_NOW = Instant.parse("2026-11-15T15:00:00Z");

    private AllowanceRepository allowanceRepository;
    private AllowanceReadQuery readQuery;
    private FamilyQueryApi familyQueryApi;
    private AccountQueryApi accountQueryApi;
    private CategoryQueryApi categoryQueryApi;
    private EconomicsApi economicsApi;
    private TransactionCommandApi transactionCommandApi;
    private ApplicationEventPublisher eventPublisher;
    private AllowanceService service;

    private UUID familyId;
    private UUID adultId;
    private UUID otherAdultId;
    private UUID childId;
    private UUID sourceAccountId;
    private UUID targetAccountId;
    private CurrentUser adultUser;
    private CurrentUser childUser;
    private FamilySettings settings;

    @BeforeEach
    void setUp() {
        allowanceRepository = mock(AllowanceRepository.class);
        readQuery = mock(AllowanceReadQuery.class);
        familyQueryApi = mock(FamilyQueryApi.class);
        accountQueryApi = mock(AccountQueryApi.class);
        categoryQueryApi = mock(CategoryQueryApi.class);
        economicsApi = mock(EconomicsApi.class);
        transactionCommandApi = mock(TransactionCommandApi.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

        service = new AllowanceService(
                allowanceRepository,
                readQuery,
                familyQueryApi,
                accountQueryApi,
                categoryQueryApi,
                economicsApi,
                transactionCommandApi,
                eventPublisher,
                clock);

        familyId = UUID.randomUUID();
        adultId = UUID.randomUUID();
        otherAdultId = UUID.randomUUID();
        childId = UUID.randomUUID();
        sourceAccountId = UUID.randomUUID();
        targetAccountId = UUID.randomUUID();

        adultUser = new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childUser = new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        settings = new FamilySettings(
                familyId, "Test Family", "ARS", "USD", "USD_MEP", AR_ZONE, List.of("ARS", "USD"));

        when(familyQueryApi.getSettings(familyId)).thenReturn(settings);
        when(familyQueryApi.requireMember(familyId, adultId))
                .thenReturn(new MemberRef(adultId, familyId, "Parent", Role.ADULTO, "#2563EB"));
        when(familyQueryApi.requireMember(familyId, otherAdultId))
                .thenReturn(
                        new MemberRef(otherAdultId, familyId, "Other Parent", Role.ADULTO, "#16A34A"));
        when(familyQueryApi.requireMember(familyId, childId))
                .thenReturn(new MemberRef(childId, familyId, "Junior", Role.HIJO, "#F59E0B"));

        when(accountQueryApi.requireUsable(adultUser, sourceAccountId))
                .thenReturn(new AccountRef(
                        sourceAccountId, familyId, adultId, "Parent Bank", "BANK", "ARS", null, null));
        when(accountQueryApi.requireUsable(adultUser, targetAccountId))
                .thenReturn(new AccountRef(
                        targetAccountId, familyId, childId, "Child Wallet", "WALLET", "ARS", null, null));
    }

    @Test
    void childCannotCreateAllowance() {
        CreateAllowanceCommand cmd = new CreateAllowanceCommand(
                childId,
                sourceAccountId,
                targetAccountId,
                new BigDecimal("50000"),
                "ARS",
                AllowanceFrequency.MONTHLY,
                5,
                AllowanceAdjustmentRule.MANUAL);

        assertThatThrownBy(() -> service.create(childUser, cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createRejectsAdultAsChildAndNonMemberAsChild() {
        CreateAllowanceCommand adultAsChild = new CreateAllowanceCommand(
                otherAdultId,
                sourceAccountId,
                targetAccountId,
                new BigDecimal("50000"),
                "ARS",
                AllowanceFrequency.MONTHLY,
                5,
                AllowanceAdjustmentRule.MANUAL);

        assertThatThrownBy(() -> service.create(adultUser, adultAsChild))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("HIJO");

        UUID strangerId = UUID.randomUUID();
        when(familyQueryApi.requireMember(familyId, strangerId))
                .thenThrow(new NotFoundException("Family member not found"));

        CreateAllowanceCommand strangerAsChild = new CreateAllowanceCommand(
                strangerId,
                sourceAccountId,
                targetAccountId,
                new BigDecimal("50000"),
                "ARS",
                AllowanceFrequency.MONTHLY,
                5,
                AllowanceAdjustmentRule.MANUAL);

        assertThatThrownBy(() -> service.create(adultUser, strangerAsChild))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createRejectsTargetAccountNotOwnedByChild() {
        UUID sharedAccountId = UUID.randomUUID();
        when(accountQueryApi.requireUsable(adultUser, sharedAccountId))
                .thenReturn(new AccountRef(
                        sharedAccountId, familyId, null, "Shared", "BANK", "ARS", null, null));

        CreateAllowanceCommand adultOwnedTarget = new CreateAllowanceCommand(
                childId,
                null,
                sourceAccountId,
                new BigDecimal("50000"),
                "ARS",
                AllowanceFrequency.MONTHLY,
                5,
                AllowanceAdjustmentRule.MANUAL);
        assertThatThrownBy(() -> service.create(adultUser, adultOwnedTarget))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("child");

        CreateAllowanceCommand sharedTarget = new CreateAllowanceCommand(
                childId,
                null,
                sharedAccountId,
                new BigDecimal("50000"),
                "ARS",
                AllowanceFrequency.MONTHLY,
                5,
                AllowanceAdjustmentRule.MANUAL);
        assertThatThrownBy(() -> service.create(adultUser, sharedTarget))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("child");
    }

    @Test
    void createRejectsWeeklyDayGreaterThanSeven() {
        CreateAllowanceCommand cmd = new CreateAllowanceCommand(
                childId,
                sourceAccountId,
                targetAccountId,
                new BigDecimal("10000"),
                "ARS",
                AllowanceFrequency.WEEKLY,
                8,
                AllowanceAdjustmentRule.MANUAL);

        assertThatThrownBy(() -> service.create(adultUser, cmd))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("WEEKLY");
    }

    @Test
    void disburseWithSourceAccountCreatesPairedTransactionsAndPublishesEvent() {
        UUID allowanceId = UUID.randomUUID();
        Allowance entity = Allowance.create(
                familyId,
                adultId,
                childId,
                sourceAccountId,
                targetAccountId,
                new BigDecimal("50000"),
                "ARS",
                AllowanceFrequency.MONTHLY,
                15,
                AllowanceAdjustmentRule.MANUAL,
                FIXED_NOW);
        ReflectionTestUtils.setField(entity, "id", allowanceId);

        when(allowanceRepository.findByIdAndFamilyIdForUpdate(allowanceId, familyId))
                .thenReturn(Optional.of(entity));

        UUID categoryId = UUID.randomUUID();
        when(categoryQueryApi.requireSystemCategory("ALLOWANCE_INCOME"))
                .thenReturn(new CategoryRef(
                        categoryId, "Mensualidad", "INCOME", "piggy-bank", null));

        UUID outTxId = UUID.randomUUID();
        UUID inTxId = UUID.randomUUID();
        when(transactionCommandApi.record(any(NewTransactionCommand.class)))
                .thenReturn(outTxId, inTxId);

        when(readQuery.findById(familyId, allowanceId))
                .thenReturn(Optional.of(new AllowanceReadRow(
                        allowanceId,
                        familyId,
                        childId,
                        "Junior",
                        "#F59E0B",
                        adultId,
                        "Parent",
                        sourceAccountId,
                        "Parent Bank",
                        targetAccountId,
                        "Child Wallet",
                        new BigDecimal("50000.0000"),
                        "ARS",
                        AllowanceFrequency.MONTHLY,
                        15,
                        AllowanceAdjustmentRule.MANUAL,
                        true,
                        FIXED_NOW)));
        when(readQuery.findLastDisbursementDates(List.of(allowanceId)))
                .thenReturn(Map.of(allowanceId, LocalDate.of(2026, 11, 15)));
        when(accountQueryApi.currentBalance(targetAccountId))
                .thenReturn(new BigDecimal("50000.0000"));

        DisbursementResult result =
                service.disburse(adultUser, allowanceId, new DisburseAllowanceCommand(null, null));

        assertThat(result.outTransactionId()).isEqualTo(outTxId);
        assertThat(result.inTransactionId()).isEqualTo(inTxId);
        assertThat(result.allowance().childBalance()).isEqualByComparingTo("50000.0000");
        assertThat(result.allowance().lastDisbursementDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(result.allowance().nextDisbursementDate()).isEqualTo(LocalDate.of(2026, 12, 15));

        ArgumentCaptor<NewTransactionCommand> txCaptor =
                ArgumentCaptor.forClass(NewTransactionCommand.class);
        verify(transactionCommandApi, times(2)).record(txCaptor.capture());

        NewTransactionCommand outCmd = txCaptor.getAllValues().get(0);
        NewTransactionCommand inCmd = txCaptor.getAllValues().get(1);

        assertThat(outCmd.type()).isEqualTo(TransactionType.ALLOWANCE_OUT);
        assertThat(outCmd.accountId()).isEqualTo(sourceAccountId);
        assertThat(outCmd.userId()).isEqualTo(adultId);
        assertThat(outCmd.attributedToUserId()).isEqualTo(adultId);
        assertThat(outCmd.categoryId()).isNull();
        assertThat(outCmd.scope()).isEqualTo(Scope.PERSONAL);
        assertThat(outCmd.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(outCmd.allowanceId()).isEqualTo(allowanceId);
        assertThat(outCmd.transferGroupId()).isNotNull();

        assertThat(inCmd.type()).isEqualTo(TransactionType.ALLOWANCE_IN);
        assertThat(inCmd.accountId()).isEqualTo(targetAccountId);
        assertThat(inCmd.userId()).isEqualTo(adultId);
        assertThat(inCmd.attributedToUserId()).isEqualTo(childId);
        assertThat(inCmd.categoryId()).isEqualTo(categoryId);
        assertThat(inCmd.scope()).isEqualTo(Scope.PERSONAL);
        assertThat(inCmd.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(inCmd.allowanceId()).isEqualTo(allowanceId);
        assertThat(inCmd.transferGroupId()).isEqualTo(outCmd.transferGroupId());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(new AllowanceDisbursedEvent(
                        familyId, allowanceId, childId, new BigDecimal("50000.0000"), "ARS"));
    }

    @Test
    void disburseWithoutSourceAccountCreatesOnlyAllowanceIn() {
        UUID allowanceId = UUID.randomUUID();
        Allowance entity = Allowance.create(
                familyId,
                adultId,
                childId,
                null,
                targetAccountId,
                new BigDecimal("25000"),
                "ARS",
                AllowanceFrequency.WEEKLY,
                1,
                AllowanceAdjustmentRule.MANUAL,
                FIXED_NOW);
        ReflectionTestUtils.setField(entity, "id", allowanceId);

        when(allowanceRepository.findByIdAndFamilyIdForUpdate(allowanceId, familyId))
                .thenReturn(Optional.of(entity));

        UUID categoryId = UUID.randomUUID();
        when(categoryQueryApi.requireSystemCategory("ALLOWANCE_INCOME"))
                .thenReturn(new CategoryRef(
                        categoryId, "Mensualidad", "INCOME", "piggy-bank", null));

        UUID inTxId = UUID.randomUUID();
        when(transactionCommandApi.record(any(NewTransactionCommand.class))).thenReturn(inTxId);

        when(readQuery.findById(familyId, allowanceId))
                .thenReturn(Optional.of(new AllowanceReadRow(
                        allowanceId,
                        familyId,
                        childId,
                        "Junior",
                        "#F59E0B",
                        adultId,
                        "Parent",
                        null,
                        null,
                        targetAccountId,
                        "Child Wallet",
                        new BigDecimal("25000.0000"),
                        "ARS",
                        AllowanceFrequency.WEEKLY,
                        1,
                        AllowanceAdjustmentRule.MANUAL,
                        true,
                        FIXED_NOW)));
        when(readQuery.findLastDisbursementDates(List.of(allowanceId)))
                .thenReturn(Map.of(allowanceId, LocalDate.of(2026, 11, 16)));
        when(accountQueryApi.currentBalance(targetAccountId))
                .thenReturn(new BigDecimal("25000.0000"));

        DisbursementResult result = service.disburse(
                adultUser,
                allowanceId,
                new DisburseAllowanceCommand(LocalDate.of(2026, 11, 16), new BigDecimal("30000")));

        assertThat(result.outTransactionId()).isNull();
        assertThat(result.inTransactionId()).isEqualTo(inTxId);

        ArgumentCaptor<NewTransactionCommand> txCaptor =
                ArgumentCaptor.forClass(NewTransactionCommand.class);
        verify(transactionCommandApi, times(1)).record(txCaptor.capture());
        NewTransactionCommand inCmd = txCaptor.getValue();
        assertThat(inCmd.type()).isEqualTo(TransactionType.ALLOWANCE_IN);
        assertThat(inCmd.amount()).isEqualByComparingTo("30000.0000");
        assertThat(inCmd.transactionDate()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(inCmd.transferGroupId()).isNotNull();
    }

    @Test
    void disburseRejectsInactiveAllowance() {
        UUID allowanceId = UUID.randomUUID();
        Allowance entity = Allowance.create(
                familyId,
                adultId,
                childId,
                sourceAccountId,
                targetAccountId,
                new BigDecimal("50000"),
                "ARS",
                AllowanceFrequency.MONTHLY,
                5,
                AllowanceAdjustmentRule.MANUAL,
                FIXED_NOW);
        entity.deactivate();
        ReflectionTestUtils.setField(entity, "id", allowanceId);

        when(allowanceRepository.findByIdAndFamilyIdForUpdate(eq(allowanceId), eq(familyId)))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.disburse(
                        adultUser, allowanceId, new DisburseAllowanceCommand(null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }
}
