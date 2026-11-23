package com.familion.finance.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.NotFoundException;
import com.familion.common.events.FamilyFinancialDataChangedEvent;
import com.familion.common.security.Role;
import com.familion.economics.application.api.EconomicSnapshotData;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.economics.application.api.FamilyEconomicContext;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.family.application.api.MemberRef;
import com.familion.finance.transactions.application.api.ExpenseAttributedEvent;
import com.familion.finance.transactions.application.api.NewTransactionCommand;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionChangedEvent;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.finance.transactions.domain.EconomicSnapshot;
import com.familion.finance.transactions.domain.Transaction;
import com.familion.finance.transactions.infrastructure.persistence.EconomicSnapshotRepository;
import com.familion.finance.transactions.infrastructure.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class TransactionCommandServiceTest {

    private TransactionRepository transactionRepository;
    private EconomicSnapshotRepository economicSnapshotRepository;
    private FamilyQueryApi familyQueryApi;
    private EconomicsApi economicsApi;
    private ApplicationEventPublisher eventPublisher;
    private Clock clock;
    private TransactionCommandService service;

    private final UUID familyId = UUID.randomUUID();
    private final UUID adultId = UUID.randomUUID();
    private final UUID childId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        economicSnapshotRepository = mock(EconomicSnapshotRepository.class);
        familyQueryApi = mock(FamilyQueryApi.class);
        economicsApi = mock(EconomicsApi.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        clock = Clock.fixed(Instant.parse("2026-11-23T22:00:00Z"), ZoneOffset.UTC);

        service = new TransactionCommandService(
                transactionRepository,
                economicSnapshotRepository,
                familyQueryApi,
                economicsApi,
                eventPublisher,
                clock);
    }

    @Test
    void recordRejectsZeroOrNegativeAmount() {
        NewTransactionCommand zeroCmd = command(BigDecimal.ZERO, adultId, TransactionType.EXPENSE);
        assertThatThrownBy(() -> service.record(zeroCmd))
                .isInstanceOf(BusinessRuleException.class);

        NewTransactionCommand negCmd =
                command(new BigDecimal("-10.00"), adultId, TransactionType.EXPENSE);
        assertThatThrownBy(() -> service.record(negCmd))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void recordValidatesMembershipCreatesSnapshotAndPublishesAttributionEvent() {
        UUID savedId = UUID.randomUUID();
        FamilySettings settings = new FamilySettings(
                familyId,
                "Familia Test",
                "ARS",
                "USD",
                "USD_MEP",
                ZoneId.of("America/Argentina/Buenos_Aires"),
                List.of("ARS", "USD"));
        when(familyQueryApi.requireMember(familyId, adultId))
                .thenReturn(new MemberRef(adultId, familyId, "Adult", Role.ADULTO, "#000000"));
        when(familyQueryApi.requireMember(familyId, childId))
                .thenReturn(new MemberRef(childId, familyId, "Child", Role.HIJO, "#111111"));
        when(familyQueryApi.getSettings(familyId)).thenReturn(settings);

        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            ReflectionTestUtils.setField(tx, "id", savedId);
            return tx;
        });

        EconomicSnapshotData snapshotData = new EconomicSnapshotData(
                "USD",
                "USD_MEP",
                new BigDecimal("1200.000000"),
                new BigDecimal("10.0000"),
                new BigDecimal("12000.0000"),
                new BigDecimal("250.500000"),
                new BigDecimal("1300.000000"),
                LocalDate.of(2026, 11, 20));
        when(economicsApi.snapshotFor(
                        new FamilyEconomicContext("ARS", "USD", "USD_MEP"),
                        LocalDate.of(2026, 11, 20),
                        new BigDecimal("12000.0000"),
                        "ARS"))
                .thenReturn(snapshotData);

        UUID result = service.record(command(new BigDecimal("12000"), childId, TransactionType.EXPENSE));

        assertThat(result).isEqualTo(savedId);
        verify(familyQueryApi).requireMember(familyId, adultId);
        verify(familyQueryApi).requireMember(familyId, childId);

        ArgumentCaptor<EconomicSnapshot> snapCaptor = ArgumentCaptor.forClass(EconomicSnapshot.class);
        verify(economicSnapshotRepository).saveAndFlush(snapCaptor.capture());
        assertThat(snapCaptor.getValue().getTransactionId()).isEqualTo(savedId);
        assertThat(snapCaptor.getValue().getBaseCurrencyAmount()).isEqualByComparingTo("12000.0000");

        verify(eventPublisher)
                .publishEvent(new ExpenseAttributedEvent(
                        familyId,
                        savedId,
                        adultId,
                        childId,
                        new BigDecimal("12000.0000"),
                        "ARS"));
        verify(eventPublisher).publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    @Test
    void recordDoesNotPublishExpenseAttributedEventWhenSelfAttributedOrIncome() {
        UUID savedId = UUID.randomUUID();
        FamilySettings settings = new FamilySettings(
                familyId,
                "Familia Test",
                "ARS",
                "USD",
                "USD_MEP",
                ZoneId.of("UTC"),
                List.of("ARS", "USD"));
        when(familyQueryApi.requireMember(eq(familyId), any()))
                .thenReturn(new MemberRef(adultId, familyId, "Adult", Role.ADULTO, "#000000"));
        when(familyQueryApi.getSettings(familyId)).thenReturn(settings);
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            ReflectionTestUtils.setField(tx, "id", savedId);
            return tx;
        });
        when(economicsApi.snapshotFor(any(), any(), any(), any()))
                .thenReturn(new EconomicSnapshotData(
                        "USD",
                        "USD_MEP",
                        new BigDecimal("1000.000000"),
                        new BigDecimal("1.0000"),
                        new BigDecimal("1000.0000"),
                        null,
                        null,
                        LocalDate.of(2026, 11, 20)));

        service.record(command(new BigDecimal("1000"), childId, TransactionType.INCOME));

        verify(eventPublisher, never()).publishEvent(any(ExpenseAttributedEvent.class));
        verify(eventPublisher).publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    @Test
    void updateAmountAndDateThrowsNotFoundForMissingOrOtherFamilyTransaction() {
        UUID txId = UUID.randomUUID();
        when(transactionRepository.findByIdAndFamilyIdAndDeletedAtIsNull(txId, familyId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAmountAndDate(
                        familyId, txId, new BigDecimal("500"), LocalDate.of(2026, 11, 21)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void softDeleteMarksDeletedAndPublishesEvents() {
        UUID txId = UUID.randomUUID();
        Transaction tx = Transaction.create(
                familyId,
                adultId,
                adultId,
                accountId,
                null,
                TransactionType.EXPENSE,
                Scope.PERSONAL,
                TransactionStatus.POSTED,
                new BigDecimal("500"),
                "ARS",
                LocalDate.of(2026, 11, 20),
                "Coffee",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        ReflectionTestUtils.setField(tx, "id", txId);
        when(transactionRepository.findByIdAndFamilyIdAndDeletedAtIsNull(txId, familyId))
                .thenReturn(Optional.of(tx));

        service.softDelete(familyId, txId);

        assertThat(tx.getDeletedAt()).isEqualTo(clock.instant());
        verify(eventPublisher)
                .publishEvent(new TransactionChangedEvent(
                        familyId, txId, new BigDecimal("500.0000"), LocalDate.of(2026, 11, 20), true));
        verify(eventPublisher).publishEvent(new FamilyFinancialDataChangedEvent(familyId));
    }

    private NewTransactionCommand command(
            BigDecimal amount, UUID attributedTo, TransactionType type) {
        return new NewTransactionCommand(
                familyId,
                adultId,
                attributedTo,
                accountId,
                null,
                type,
                Scope.PERSONAL,
                TransactionStatus.POSTED,
                amount,
                "ARS",
                LocalDate.of(2026, 11, 20),
                "Test tx",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
