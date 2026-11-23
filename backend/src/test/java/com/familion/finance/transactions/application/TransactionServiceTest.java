package com.familion.finance.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.ConflictException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.accounts.application.api.AccountQueryApi;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import com.familion.finance.transactions.domain.Transaction;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class TransactionServiceTest {

    private TransactionCommandService commandService;
    private TransactionQueryService queryService;
    private TransactionRepository transactionRepository;
    private AccountQueryApi accountQueryApi;
    private CategoryQueryApi categoryQueryApi;
    private FamilyQueryApi familyQueryApi;
    private ApplicationEventPublisher eventPublisher;
    private TransactionService service;

    private final UUID familyId = UUID.randomUUID();
    private final UUID adultId = UUID.randomUUID();
    private final UUID childId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final CurrentUser adult =
            new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
    private final CurrentUser child =
            new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

    @BeforeEach
    void setUp() {
        commandService = mock(TransactionCommandService.class);
        queryService = mock(TransactionQueryService.class);
        transactionRepository = mock(TransactionRepository.class);
        accountQueryApi = mock(AccountQueryApi.class);
        categoryQueryApi = mock(CategoryQueryApi.class);
        familyQueryApi = mock(FamilyQueryApi.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);

        service = new TransactionService(
                commandService,
                queryService,
                transactionRepository,
                accountQueryApi,
                categoryQueryApi,
                familyQueryApi,
                eventPublisher,
                clock);

        when(familyQueryApi.getSettings(familyId))
                .thenReturn(new FamilySettings(
                        familyId,
                        "Familia",
                        "ARS",
                        "USD",
                        "USD_MEP",
                        ZoneId.of("UTC"),
                        List.of("ARS", "USD")));
    }

    @Test
    void nonAuthorChildCannotEditOrDeleteAdultTransaction() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId, adultId, childId, TransactionType.EXPENSE, null, null);
        when(transactionRepository.findByIdAndFamilyIdAndDeletedAtIsNull(txId, familyId))
                .thenReturn(Optional.of(tx));

        TransactionPatchCommand patch = new TransactionPatchCommand(
                null, null, false, null, true, "Changed", false, null, null, null, null);

        assertThatThrownBy(() -> service.update(child, txId, patch))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(child, txId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void childAuthorCannotChangeAttributionOnEdit() {
        UUID txId = UUID.randomUUID();
        Transaction tx = buildTransaction(txId, childId, childId, TransactionType.EXPENSE, null, null);
        when(transactionRepository.findByIdAndFamilyIdAndDeletedAtIsNull(txId, familyId))
                .thenReturn(Optional.of(tx));

        TransactionPatchCommand patch = new TransactionPatchCommand(
                null, null, false, null, false, null, false, null, null, adultId, null);

        assertThatThrownBy(() -> service.update(child, txId, patch))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void exchangeAndAllowanceRejectNonDescriptionOrNotesEditsWith409() {
        UUID txId = UUID.randomUUID();
        Transaction exchange =
                buildTransaction(txId, adultId, adultId, TransactionType.EXCHANGE_OUT, null, null);
        when(transactionRepository.findByIdAndFamilyIdAndDeletedAtIsNull(txId, familyId))
                .thenReturn(Optional.of(exchange));

        TransactionPatchCommand amountPatch = new TransactionPatchCommand(
                new BigDecimal("999"), null, false, null, false, null, false, null, null, null, null);
        assertThatThrownBy(() -> service.update(adult, txId, amountPatch))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void installmentRejectsAmountOrDateEditAndRejectsDeleteWithUseCancelRemaining() {
        UUID txId = UUID.randomUUID();
        Transaction inst = buildTransaction(
                txId, adultId, adultId, TransactionType.EXPENSE, UUID.randomUUID(), null);
        when(transactionRepository.findByIdAndFamilyIdAndDeletedAtIsNull(txId, familyId))
                .thenReturn(Optional.of(inst));

        TransactionPatchCommand amountPatch = new TransactionPatchCommand(
                new BigDecimal("500"), null, false, null, false, null, false, null, null, null, null);
        assertThatThrownBy(() -> service.update(adult, txId, amountPatch))
                .isInstanceOf(ConflictException.class);

        TransactionPatchCommand datePatch = new TransactionPatchCommand(
                null, LocalDate.of(2026, 12, 1), false, null, false, null, false, null, null, null, null);
        assertThatThrownBy(() -> service.update(adult, txId, datePatch))
                .isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> service.delete(adult, txId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Use cancel-remaining");
    }

    @Test
    void deleteTransferSoftDeletesBothLegs() {
        UUID groupId = UUID.randomUUID();
        UUID outId = UUID.randomUUID();
        UUID inId = UUID.randomUUID();
        Transaction outLeg =
                buildTransaction(outId, adultId, adultId, TransactionType.TRANSFER_OUT, null, groupId);
        Transaction inLeg =
                buildTransaction(inId, adultId, adultId, TransactionType.TRANSFER_IN, null, groupId);

        when(transactionRepository.findByIdAndFamilyIdAndDeletedAtIsNull(outId, familyId))
                .thenReturn(Optional.of(outLeg));
        when(transactionRepository.findByFamilyIdAndTransferGroupIdAndDeletedAtIsNull(familyId, groupId))
                .thenReturn(List.of(outLeg, inLeg));

        service.delete(adult, outId);

        verify(commandService).softDelete(familyId, outId);
        verify(commandService).softDelete(familyId, inId);
        assertThat(outLeg.getTransferGroupId()).isEqualTo(groupId);
    }

    private Transaction buildTransaction(
            UUID id,
            UUID paidBy,
            UUID attributedTo,
            TransactionType type,
            UUID installmentPlanId,
            UUID transferGroupId) {
        Transaction tx = Transaction.create(
                familyId,
                paidBy,
                attributedTo,
                accountId,
                null,
                type,
                Scope.PERSONAL,
                TransactionStatus.POSTED,
                new BigDecimal("1000"),
                "ARS",
                LocalDate.of(2026, 11, 20),
                "Desc",
                null,
                installmentPlanId,
                installmentPlanId == null ? null : 1,
                null,
                null,
                null,
                null,
                transferGroupId);
        ReflectionTestUtils.setField(tx, "id", id);
        return tx;
    }
}
