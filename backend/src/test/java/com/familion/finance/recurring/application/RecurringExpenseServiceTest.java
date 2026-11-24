package com.familion.finance.recurring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ConflictException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.accounts.application.api.AccountRef;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.recurring.domain.AdjustmentRule;
import com.familion.finance.recurring.domain.RecurringExpense;
import com.familion.finance.recurring.infrastructure.persistence.RecurringExpenseRepository;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC);
    private static final ZoneId AR_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private RecurringExpenseRepository repository;

    @Mock
    private RecurringReadQuery readQuery;

    @Mock
    private CategoryQueryApi categoryQueryApi;

    @Mock
    private AccountQueryApi accountQueryApi;

    @Mock
    private FamilyQueryApi familyQueryApi;

    @Mock
    private EconomicsApi economicsApi;

    @Mock
    private TransactionCommandApi transactionCommandApi;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RecurringExpenseService service;
    private UUID familyId;
    private CurrentUser adult;
    private CurrentUser otherAdult;
    private CurrentUser child;
    private FamilySettings familySettings;

    @BeforeEach
    void setUp() {
        service = new RecurringExpenseService(
                repository,
                readQuery,
                categoryQueryApi,
                accountQueryApi,
                familyQueryApi,
                economicsApi,
                transactionCommandApi,
                eventPublisher,
                FIXED_CLOCK);
        familyId = UUID.randomUUID();
        adult = new CurrentUser(UUID.randomUUID(), familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        otherAdult = new CurrentUser(
                UUID.randomUUID(), familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        child = new CurrentUser(UUID.randomUUID(), familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
        familySettings = new FamilySettings(
                familyId, "Test", "ARS", "USD", "USD_MEP", AR_ZONE, List.of("ARS", "USD"));
    }

    @Test
    void createRejectsChildUser() {
        CreateRecurringExpenseCommand cmd = new CreateRecurringExpenseCommand(
                "Alquiler",
                UUID.randomUUID(),
                null,
                Scope.FAMILY,
                new BigDecimal("500000"),
                "ARS",
                10,
                AdjustmentRule.MANUAL,
                LocalDate.of(2026, 1, 1),
                null);

        assertThatThrownBy(() -> service.create(child, cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        CreateRecurringExpenseCommand cmd = new CreateRecurringExpenseCommand(
                "Alquiler",
                UUID.randomUUID(),
                null,
                Scope.FAMILY,
                new BigDecimal("500000"),
                "ARS",
                10,
                AdjustmentRule.MANUAL,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 4, 30));

        assertThatThrownBy(() -> service.create(adult, cmd))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void confirmPaymentRejectsAlreadyPaidMonthWithConflict() {
        UUID recurringId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        RecurringExpense entity = buildEntity(
                recurringId, familyId, adult.userId(), accountId, categoryId, Scope.FAMILY, true);

        when(repository.findByIdAndFamilyIdForUpdate(recurringId, familyId))
                .thenReturn(Optional.of(entity));
        when(readQuery.existsPaymentInRange(
                        familyId,
                        recurringId,
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 30)))
                .thenReturn(true);

        ConfirmPaymentCommand cmd = new ConfirmPaymentCommand(
                YearMonth.of(2026, 11), new BigDecimal("500000"), null, null);

        assertThatThrownBy(() -> service.confirmPayment(adult, recurringId, cmd))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmPaymentRejectsNotApplicableMonthWithBusinessRule() {
        UUID recurringId = UUID.randomUUID();
        RecurringExpense entity = buildEntity(
                recurringId,
                familyId,
                adult.userId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Scope.FAMILY,
                true);

        when(repository.findByIdAndFamilyIdForUpdate(recurringId, familyId))
                .thenReturn(Optional.of(entity));

        ConfirmPaymentCommand cmd = new ConfirmPaymentCommand(
                YearMonth.of(2025, 12), new BigDecimal("500000"), null, null);

        assertThatThrownBy(() -> service.confirmPayment(adult, recurringId, cmd))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void confirmPaymentRejectsWhenNoAccountProvidedOrSuggested() {
        UUID recurringId = UUID.randomUUID();
        RecurringExpense entity = buildEntity(
                recurringId, familyId, adult.userId(), null, UUID.randomUUID(), Scope.FAMILY, true);

        when(repository.findByIdAndFamilyIdForUpdate(recurringId, familyId))
                .thenReturn(Optional.of(entity));
        when(readQuery.existsPaymentInRange(any(), any(), any(), any())).thenReturn(false);

        ConfirmPaymentCommand cmd = new ConfirmPaymentCommand(
                YearMonth.of(2026, 11), new BigDecimal("500000"), null, null);

        assertThatThrownBy(() -> service.confirmPayment(adult, recurringId, cmd))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void confirmPaymentRejectsOtherMemberForPersonalExpense() {
        UUID recurringId = UUID.randomUUID();
        RecurringExpense entity = buildEntity(
                recurringId,
                familyId,
                adult.userId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Scope.PERSONAL,
                true);

        when(repository.findByIdAndFamilyIdForUpdate(recurringId, familyId))
                .thenReturn(Optional.of(entity));

        ConfirmPaymentCommand cmd = new ConfirmPaymentCommand(
                YearMonth.of(2026, 11), new BigDecimal("10000"), null, null);

        assertThatThrownBy(() -> service.confirmPayment(otherAdult, recurringId, cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void confirmPaymentAllowsChildToConfirmFamilyExpenseWithOwnAccount() {
        UUID recurringId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID childAccountId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        RecurringExpense entity = buildEntity(
                recurringId, familyId, adult.userId(), null, categoryId, Scope.FAMILY, true);

        when(repository.findByIdAndFamilyIdForUpdate(recurringId, familyId))
                .thenReturn(Optional.of(entity));
        when(readQuery.existsPaymentInRange(any(), any(), any(), any())).thenReturn(false);
        when(accountQueryApi.requireUsable(child, childAccountId))
                .thenReturn(new AccountRef(
                        childAccountId,
                        familyId,
                        child.userId(),
                        "Billetera Hijo",
                        "CASH",
                        "ARS",
                        null,
                        null));
        when(familyQueryApi.getSettings(familyId)).thenReturn(familySettings);
        when(transactionCommandApi.record(any())).thenReturn(txId);
        when(readQuery.findById(familyId, recurringId))
                .thenReturn(Optional.of(new RecurringReadRow(
                        recurringId,
                        familyId,
                        adult.userId(),
                        "Alquiler",
                        categoryId,
                        "Vivienda",
                        "home",
                        "#123456",
                        null,
                        null,
                        Scope.FAMILY,
                        new BigDecimal("500000.0000"),
                        "ARS",
                        10,
                        AdjustmentRule.MANUAL,
                        LocalDate.of(2026, 1, 1),
                        null,
                        true)));
        when(readQuery.findPaymentsInRange(any(), any(), any()))
                .thenReturn(Map.of(
                        recurringId,
                        new RecurringPaymentRow(
                                recurringId,
                                txId,
                                new BigDecimal("520000.0000"),
                                LocalDate.of(2026, 11, 15))));

        RecurringExpenseView view = service.confirmPayment(
                child,
                recurringId,
                new ConfirmPaymentCommand(
                        YearMonth.of(2026, 11), new BigDecimal("520000"), childAccountId, null));

        ArgumentCaptor<NewTransactionCommand> txCaptor =
                ArgumentCaptor.forClass(NewTransactionCommand.class);
        verify(transactionCommandApi).record(txCaptor.capture());

        NewTransactionCommand recorded = txCaptor.getValue();
        assertThat(recorded.familyId()).isEqualTo(familyId);
        assertThat(recorded.userId()).isEqualTo(child.userId());
        assertThat(recorded.attributedToUserId()).isEqualTo(child.userId());
        assertThat(recorded.accountId()).isEqualTo(childAccountId);
        assertThat(recorded.categoryId()).isEqualTo(categoryId);
        assertThat(recorded.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(recorded.scope()).isEqualTo(Scope.FAMILY);
        assertThat(recorded.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(recorded.amount()).isEqualByComparingTo("520000.0000");
        assertThat(recorded.currencyCode()).isEqualTo("ARS");
        assertThat(recorded.transactionDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(recorded.description()).isEqualTo("Alquiler");
        assertThat(recorded.recurringExpenseId()).isEqualTo(recurringId);

        assertThat(view.month().paidTransactionId()).isEqualTo(txId);
        assertThat(view.month().paidAmount()).isEqualByComparingTo("520000.0000");
    }

    private static RecurringExpense buildEntity(
            UUID id,
            UUID familyId,
            UUID createdByUserId,
            UUID accountId,
            UUID categoryId,
            Scope scope,
            boolean active) {
        RecurringExpense entity = RecurringExpense.create(
                familyId,
                createdByUserId,
                accountId,
                categoryId,
                "Alquiler",
                scope,
                new BigDecimal("500000"),
                "ARS",
                10,
                AdjustmentRule.MANUAL,
                LocalDate.of(2026, 1, 1),
                null);
        if (!active) {
            entity.deactivate();
        }
        try {
            Field idField = RecurringExpense.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return entity;
    }
}
