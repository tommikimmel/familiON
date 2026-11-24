package com.familion.finance.installments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.ForbiddenException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.DefaultVisibilityPolicy;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.security.View;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.installments.domain.InstallmentPlanStatus;
import com.familion.finance.transactions.application.api.TransactionStatus;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstallmentPlanQueryServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-04-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private InstallmentPlanReadQuery readQuery;

    @Mock
    private FamilyQueryApi familyQueryApi;

    @Mock
    private EconomicsApi economicsApi;

    private InstallmentPlanQueryService service;

    private UUID familyId;
    private UUID adultId;
    private UUID childId;
    private CurrentUser adult;
    private CurrentUser childOwnOnly;

    @BeforeEach
    void setUp() {
        service = new InstallmentPlanQueryService(
                readQuery,
                new DefaultVisibilityPolicy(),
                familyQueryApi,
                economicsApi,
                FIXED_CLOCK);

        familyId = UUID.randomUUID();
        adultId = UUID.randomUUID();
        childId = UUID.randomUUID();
        adult = new CurrentUser(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childOwnOnly = new CurrentUser(childId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
    }

    @Test
    void listUsesPersonalViewerIdAndComputesMetricsInBatch() {
        UUID planId = UUID.randomUUID();
        InstallmentPlanReadRow row = sampleRow(planId, adultId);
        List<InstallmentTransactionRow> txs = List.of(
                new InstallmentTransactionRow(
                        UUID.randomUUID(),
                        planId,
                        1,
                        LocalDate.of(2026, 4, 5),
                        new BigDecimal("100.0000"),
                        TransactionStatus.POSTED,
                        adultId,
                        "Adult User"),
                new InstallmentTransactionRow(
                        UUID.randomUUID(),
                        planId,
                        2,
                        LocalDate.of(2026, 5, 5),
                        new BigDecimal("100.0000"),
                        TransactionStatus.PROJECTED,
                        adultId,
                        "Adult User"),
                new InstallmentTransactionRow(
                        UUID.randomUUID(),
                        planId,
                        3,
                        LocalDate.of(2026, 6, 5),
                        new BigDecimal("100.0000"),
                        TransactionStatus.PROJECTED,
                        adultId,
                        "Adult User"));

        when(readQuery.findPlans(familyId, adultId, InstallmentPlanStatus.ACTIVE))
                .thenReturn(List.of(row));
        when(readQuery.findInstallmentsByPlanIds(familyId, List.of(planId))).thenReturn(txs);
        when(familyQueryApi.getSettings(familyId))
                .thenReturn(new FamilySettings(
                        familyId,
                        "Family",
                        "ARS",
                        "USD",
                        "USD_MEP",
                        ZoneId.of("America/Argentina/Buenos_Aires"),
                        List.of("ARS")));
        when(economicsApi.latestValue("IPC_MENSUAL")).thenReturn(Optional.empty());

        List<InstallmentPlanView> result =
                service.list(adult, InstallmentPlanStatus.ACTIVE, View.PERSONAL);

        assertThat(result).hasSize(1);
        InstallmentPlanView plan = result.getFirst();
        assertThat(plan.paidInstallments()).isEqualTo(1);
        assertThat(plan.remainingAmount()).isEqualByComparingTo("200.0000");
        assertThat(plan.realRemainingAmount()).isNull();
        assertThat(plan.surchargeAmount()).isEqualByComparingTo("50.0000");
        assertThat(plan.surchargePct()).isEqualByComparingTo("20.0000");
        assertThat(plan.nextInstallment().number()).isEqualTo(2);
        verify(readQuery).findPlans(familyId, adultId, InstallmentPlanStatus.ACTIVE);
    }

    @Test
    void listRejectsOwnOnlyChildRequestingFamilyView() {
        assertThatThrownBy(() -> service.list(childOwnOnly, null, View.FAMILY))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getByIdRejectsOwnOnlyChildViewingAdultPlan() {
        UUID planId = UUID.randomUUID();
        InstallmentPlanReadRow row = sampleRow(planId, adultId);
        when(readQuery.findPlanById(familyId, planId)).thenReturn(Optional.of(row));
        when(readQuery.findInstallmentsByPlanIds(familyId, List.of(planId)))
                .thenReturn(List.of(new InstallmentTransactionRow(
                        UUID.randomUUID(),
                        planId,
                        1,
                        LocalDate.of(2026, 4, 5),
                        new BigDecimal("100.0000"),
                        TransactionStatus.POSTED,
                        adultId,
                        "Adult User")));

        assertThatThrownBy(() -> service.getById(childOwnOnly, planId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void commitmentClampsMonthsToTwentyFourAndQueriesRange() {
        when(familyQueryApi.getSettings(familyId))
                .thenReturn(new FamilySettings(
                        familyId,
                        "Family",
                        "ARS",
                        "USD",
                        "USD_MEP",
                        ZoneId.of("America/Argentina/Buenos_Aires"),
                        List.of("ARS", "USD")));
        when(readQuery.findMonthlyCommitment(
                        familyId,
                        null,
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2028, 3, 31)))
                .thenReturn(List.of(
                        new CommitmentRow("2026-05", new BigDecimal("500.0000"), "ARS")));

        List<InstallmentCommitmentView> result = service.commitment(adult, 36, View.FAMILY);

        assertThat(result)
                .containsExactly(
                        new InstallmentCommitmentView(
                                "2026-05", new BigDecimal("500.0000"), "ARS"));
        verify(readQuery)
                .findMonthlyCommitment(
                        familyId,
                        null,
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2028, 3, 31));
    }

    private InstallmentPlanReadRow sampleRow(UUID planId, UUID userId) {
        return new InstallmentPlanReadRow(
                planId,
                familyId,
                userId,
                "Fridge",
                UUID.randomUUID(),
                "Home",
                "home",
                "#223344",
                UUID.randomUUID(),
                "Visa",
                25,
                5,
                "ARS",
                3,
                true,
                new BigDecimal("250.0000"),
                new BigDecimal("300.0000"),
                new BigDecimal("100.0000"),
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 4, 5),
                InstallmentPlanStatus.ACTIVE,
                "Adult User");
    }
}
