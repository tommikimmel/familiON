package com.familion.finance.installments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InstallmentScheduleCalculatorTest {

    private final InstallmentScheduleCalculator calculator = new InstallmentScheduleCalculator();

    @Test
    void purchaseBeforeClosingBillsNextMonthOnDueDay() {
        InstallmentScheduleCalculator.PlanSchedule schedule = calculator.calculate(
                LocalDate.of(2026, 3, 20),
                3,
                AmountMode.PER_INSTALLMENT,
                new BigDecimal("1000"),
                25,
                5);

        assertThat(schedule.firstBillingDate()).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(schedule.lastBillingDate()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(schedule.installments())
                .extracting(InstallmentScheduleCalculator.ScheduledInstallment::date)
                .containsExactly(
                        LocalDate.of(2026, 4, 5),
                        LocalDate.of(2026, 5, 5),
                        LocalDate.of(2026, 6, 5));
    }

    @Test
    void purchaseOnExactClosingDayStillBillsNextMonth() {
        InstallmentScheduleCalculator.PlanSchedule schedule = calculator.calculate(
                LocalDate.of(2026, 3, 25),
                2,
                AmountMode.PER_INSTALLMENT,
                new BigDecimal("500"),
                25,
                5);

        assertThat(schedule.firstBillingDate()).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(schedule.lastBillingDate()).isEqualTo(LocalDate.of(2026, 5, 5));
    }

    @Test
    void purchaseAfterClosingShiftsBillingByOneExtraMonth() {
        InstallmentScheduleCalculator.PlanSchedule schedule = calculator.calculate(
                LocalDate.of(2026, 3, 26),
                3,
                AmountMode.PER_INSTALLMENT,
                new BigDecimal("1000"),
                25,
                5);

        assertThat(schedule.firstBillingDate()).isEqualTo(LocalDate.of(2026, 5, 5));
        assertThat(schedule.lastBillingDate()).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(schedule.installments())
                .extracting(InstallmentScheduleCalculator.ScheduledInstallment::date)
                .containsExactly(
                        LocalDate.of(2026, 5, 5),
                        LocalDate.of(2026, 6, 5),
                        LocalDate.of(2026, 7, 5));
    }

    @Test
    void clampsClosingDayAndDueDayAcrossThirtyDayMonthsAndFebruary() {
        // Closing 31 in Feb 2026 clamps to Feb 28; purchase on Feb 28 is <= closingDate -> bills Mar 31
        InstallmentScheduleCalculator.PlanSchedule schedule = calculator.calculate(
                LocalDate.of(2026, 2, 28),
                4,
                AmountMode.PER_INSTALLMENT,
                new BigDecimal("250"),
                31,
                31);

        assertThat(schedule.firstBillingDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(schedule.lastBillingDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(schedule.installments())
                .extracting(InstallmentScheduleCalculator.ScheduledInstallment::date)
                .containsExactly(
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 4, 30),
                        LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 6, 30));
    }

    @Test
    void clampsDueDayInFebruaryNonLeapAndLeapYearAndRestores31stInMarch() {
        InstallmentScheduleCalculator.PlanSchedule nonLeap = calculator.calculate(
                LocalDate.of(2025, 12, 20),
                4,
                AmountMode.PER_INSTALLMENT,
                new BigDecimal("100"),
                25,
                31);

        assertThat(nonLeap.installments())
                .extracting(InstallmentScheduleCalculator.ScheduledInstallment::date)
                .containsExactly(
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 4, 30));

        // First billing lands in Feb 2028 (leap year -> Feb 29), next month restores 31st
        InstallmentScheduleCalculator.PlanSchedule leap = calculator.calculate(
                LocalDate.of(2028, 1, 15),
                3,
                AmountMode.PER_INSTALLMENT,
                new BigDecimal("100"),
                25,
                31);

        assertThat(leap.firstBillingDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(leap.installments())
                .extracting(InstallmentScheduleCalculator.ScheduledInstallment::date)
                .containsExactly(
                        LocalDate.of(2028, 2, 29),
                        LocalDate.of(2028, 3, 31),
                        LocalDate.of(2028, 4, 30));
    }

    @Test
    void fallsBackToTenthOfNextMonthWhenClosingOrDueDayMissing() {
        InstallmentScheduleCalculator.PlanSchedule noClosing = calculator.calculate(
                LocalDate.of(2026, 3, 29),
                2,
                AmountMode.PER_INSTALLMENT,
                new BigDecimal("100"),
                null,
                5);

        assertThat(noClosing.firstBillingDate()).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(noClosing.lastBillingDate()).isEqualTo(LocalDate.of(2026, 5, 10));

        InstallmentScheduleCalculator.PlanSchedule noDue = calculator.calculate(
                LocalDate.of(2026, 12, 31),
                2,
                AmountMode.PER_INSTALLMENT,
                new BigDecimal("100"),
                25,
                null);

        assertThat(noDue.firstBillingDate()).isEqualTo(LocalDate.of(2027, 1, 10));
        assertThat(noDue.lastBillingDate()).isEqualTo(LocalDate.of(2027, 2, 10));
    }

    @Test
    void totalModeSplitsWithHalfEvenAndLastInstallmentAbsorbsRoundingDiff() {
        InstallmentScheduleCalculator.PlanSchedule schedule = calculator.calculate(
                LocalDate.of(2026, 3, 20),
                3,
                AmountMode.TOTAL,
                new BigDecimal("100.00"),
                25,
                5);

        assertThat(schedule.financedTotal()).isEqualByComparingTo("100.0000");
        assertThat(schedule.installmentAmount()).isEqualByComparingTo("33.3333");
        assertThat(schedule.installments())
                .extracting(InstallmentScheduleCalculator.ScheduledInstallment::amount)
                .containsExactly(
                        new BigDecimal("33.3333"),
                        new BigDecimal("33.3333"),
                        new BigDecimal("33.3334"));

        BigDecimal sum = schedule.installments().stream()
                .map(InstallmentScheduleCalculator.ScheduledInstallment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(schedule.financedTotal());
    }

    @Test
    void totalModeExactSumHoldsForAllInstallmentCountsBetweenTwoAndSixty() {
        BigDecimal total = new BigDecimal("1000.00");
        for (int n = 2; n <= 60; n++) {
            InstallmentScheduleCalculator.PlanSchedule schedule = calculator.calculate(
                    LocalDate.of(2026, 3, 15), n, AmountMode.TOTAL, total, 25, 5);
            BigDecimal sum = schedule.installments().stream()
                    .map(InstallmentScheduleCalculator.ScheduledInstallment::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum)
                    .as("Sum of %d installments must equal financedTotal", n)
                    .isEqualByComparingTo(schedule.financedTotal());
            assertThat(schedule.installments()).hasSize(n);
        }
    }

    @Test
    void perInstallmentModeMultipliesAmountByInstallmentCount() {
        InstallmentScheduleCalculator.PlanSchedule schedule = calculator.calculate(
                LocalDate.of(2026, 3, 20),
                6,
                AmountMode.PER_INSTALLMENT,
                new BigDecimal("12500.50"),
                25,
                5);

        assertThat(schedule.installmentAmount()).isEqualByComparingTo("12500.5000");
        assertThat(schedule.financedTotal()).isEqualByComparingTo("75003.0000");
        assertThat(schedule.installments())
                .allSatisfy(i -> assertThat(i.amount()).isEqualByComparingTo("12500.5000"));
    }

    @Test
    void rejectsOutOfRangeInstallmentsOrNonPositiveAmount() {
        assertThatThrownBy(() -> calculator.calculate(
                        LocalDate.of(2026, 3, 20), 1, AmountMode.TOTAL, new BigDecimal("100"), 25, 5))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> calculator.calculate(
                        LocalDate.of(2026, 3, 20),
                        61,
                        AmountMode.TOTAL,
                        new BigDecimal("100"),
                        25,
                        5))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> calculator.calculate(
                        LocalDate.of(2026, 3, 20), 3, AmountMode.TOTAL, BigDecimal.ZERO, 25, 5))
                .isInstanceOf(BusinessRuleException.class);
    }
}
