package com.familion.finance.installments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.familion.finance.installments.domain.InstallmentPlan;
import com.familion.finance.installments.domain.InstallmentPlanStatus;
import com.familion.finance.installments.infrastructure.persistence.InstallmentPlanRepository;
import com.familion.finance.transactions.application.api.TransactionCommandApi;
import com.familion.finance.transactions.application.api.TransactionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstallmentPostingServiceTest {

    // 2026-06-05 06:15 UTC = 2026-06-05 03:15 America/Argentina/Buenos_Aires
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-05T06:15:00Z"), ZoneOffset.UTC);

    @Mock
    private InstallmentPlanReadQuery readQuery;

    @Mock
    private InstallmentPlanRepository planRepository;

    @Mock
    private TransactionCommandApi transactionCommandApi;

    private InstallmentPostingService service;

    @BeforeEach
    void setUp() {
        service = new InstallmentPostingService(
                readQuery, planRepository, transactionCommandApi, FIXED_CLOCK);
    }

    @Test
    void postsDueInstallmentsAndCompletesFinishedPlans() {
        UUID familyId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        InstallmentPlan plan = InstallmentPlan.create(
                familyId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Sofa",
                "ARS",
                2,
                false,
                null,
                new BigDecimal("20000"),
                new BigDecimal("10000"),
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 5, 5),
                InstallmentPlanStatus.ACTIVE);

        when(readQuery.findDueProjectedInstallments(LocalDate.of(2026, 6, 5)))
                .thenReturn(List.of(new DueInstallmentRow(familyId, txId, planId)));
        when(readQuery.findActivePlansWithAllPostedInstallments()).thenReturn(List.of(planId));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        InstallmentPostingService.PostingResult result = service.postDueInstallments();

        assertThat(result.postedInstallments()).isEqualTo(1);
        assertThat(result.completedPlans()).isEqualTo(1);
        assertThat(plan.getStatus()).isEqualTo(InstallmentPlanStatus.COMPLETED);
        verify(transactionCommandApi).changeStatus(familyId, txId, TransactionStatus.POSTED);
        verify(planRepository).saveAndFlush(plan);
    }

    @Test
    void isIdempotentWhenNothingIsDue() {
        when(readQuery.findDueProjectedInstallments(LocalDate.of(2026, 6, 5)))
                .thenReturn(List.of());
        when(readQuery.findActivePlansWithAllPostedInstallments()).thenReturn(List.of());

        InstallmentPostingService.PostingResult result = service.postDueInstallments();

        assertThat(result.postedInstallments()).isZero();
        assertThat(result.completedPlans()).isZero();
        verifyNoInteractions(transactionCommandApi, planRepository);
    }
}
