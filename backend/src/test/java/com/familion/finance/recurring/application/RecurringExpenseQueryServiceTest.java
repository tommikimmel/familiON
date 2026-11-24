package com.familion.finance.recurring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.recurring.application.api.RecurringDue;
import com.familion.finance.recurring.domain.AdjustmentRule;
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
class RecurringExpenseQueryServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-02-20T15:00:00Z"), ZoneOffset.UTC);
    private static final ZoneId AR_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mock
    private RecurringReadQuery readQuery;

    @Mock
    private FamilyQueryApi familyQueryApi;

    @Mock
    private EconomicsApi economicsApi;

    private RecurringExpenseQueryService service;

    @BeforeEach
    void setUp() {
        service = new RecurringExpenseQueryService(
                readQuery, familyQueryApi, economicsApi, FIXED_CLOCK);
    }

    @Test
    void returnsEmptyListWhenToIsBeforeFrom() {
        List<RecurringDue> dues = service.pendingDueBetween(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1));

        assertThat(dues).isEmpty();
        verifyNoInteractions(readQuery, familyQueryApi, economicsApi);
    }

    @Test
    void handlesMultiMonthRangeClampingFebruaryAndApplyingLastPaidRule() {
        UUID familyId = UUID.randomUUID();
        UUID expId = UUID.randomUUID();

        RecurringDueCandidateRow candidate = new RecurringDueCandidateRow(
                expId,
                familyId,
                "Alquiler",
                new BigDecimal("100000.0000"),
                "ARS",
                31,
                AdjustmentRule.LAST_PAID,
                LocalDate.of(2026, 1, 1),
                null);

        when(readQuery.findAllActiveCandidates(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of(candidate));
        // Paid in January (110000), unpaid in February and March
        when(readQuery.findAllPaymentsUpTo(List.of(expId), LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of(new RecurringPaymentRow(
                        expId,
                        UUID.randomUUID(),
                        new BigDecimal("110000.0000"),
                        LocalDate.of(2026, 1, 28))));
        when(familyQueryApi.getSettings(familyId))
                .thenReturn(new FamilySettings(
                        familyId, "Fam", "ARS", "USD", "USD_MEP", AR_ZONE, List.of("ARS", "USD")));
        when(economicsApi.latestValue("IPC_MENSUAL"))
                .thenReturn(Optional.of(new BigDecimal("5.000000")));

        List<RecurringDue> dues = service.pendingDueBetween(
                LocalDate.of(2026, 1, 25), LocalDate.of(2026, 3, 31));

        // January skipped (paid); February clamped to 2026-02-28; March on 2026-03-31
        assertThat(dues).hasSize(2);
        assertThat(dues.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(dues.get(0).estimatedAmount()).isEqualByComparingTo("115500.0000");
        assertThat(dues.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(dues.get(1).estimatedAmount()).isEqualByComparingTo("115500.0000");
    }

    @Test
    void respectsEndDateAndExcludesOutOfRangeDueDates() {
        UUID familyId = UUID.randomUUID();
        UUID endedId = UUID.randomUUID();

        when(readQuery.findAllActiveCandidates(any(), any()))
                .thenReturn(List.of(new RecurringDueCandidateRow(
                        endedId,
                        familyId,
                        "Gym",
                        new BigDecimal("20000.0000"),
                        "ARS",
                        10,
                        AdjustmentRule.MANUAL,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 28))));
        when(readQuery.findAllPaymentsUpTo(eq(List.of(endedId)), any())).thenReturn(List.of());
        when(familyQueryApi.getSettings(familyId))
                .thenReturn(new FamilySettings(
                        familyId, "Fam", "ARS", "USD", "USD_MEP", AR_ZONE, List.of("ARS")));

        List<RecurringDue> dues = service.pendingDueBetween(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 31));

        assertThat(dues).hasSize(1);
        assertThat(dues.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 2, 10));
    }
}
