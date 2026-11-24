package com.familion.finance.installments.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstallmentMetricsCalculatorTest {

    private final InstallmentMetricsCalculator calculator = new InstallmentMetricsCalculator();

    @Test
    void calculatesSurchargeAmountAndPercentageWhenHasInterestAndCashPricePresent() {
        InstallmentMetricsCalculator.SurchargeResult result = calculator.calculateSurcharge(
                true, new BigDecimal("100000"), new BigDecimal("125000"));

        assertThat(result.surchargeAmount()).isEqualByComparingTo("25000.0000");
        assertThat(result.surchargePct()).isEqualByComparingTo("25.0000");
    }

    @Test
    void returnsNullSurchargeWhenHasInterestIsFalseEvenIfCashPriceProvided() {
        InstallmentMetricsCalculator.SurchargeResult result = calculator.calculateSurcharge(
                false, new BigDecimal("100000"), new BigDecimal("125000"));

        assertThat(result.surchargeAmount()).isNull();
        assertThat(result.surchargePct()).isNull();
    }

    @Test
    void returnsNullSurchargeWhenCashPriceIsNull() {
        InstallmentMetricsCalculator.SurchargeResult result =
                calculator.calculateSurcharge(true, null, new BigDecimal("125000"));

        assertThat(result.surchargeAmount()).isNull();
        assertThat(result.surchargePct()).isNull();
    }

    @Test
    void discountsProjectedInstallmentsUsingLatestMonthlyIpc() {
        LocalDate today = LocalDate.of(2026, 3, 20);
        List<InstallmentMetricsCalculator.ProjectedInstallment> projected = List.of(
                new InstallmentMetricsCalculator.ProjectedInstallment(
                        LocalDate.of(2026, 4, 5), new BigDecimal("100.0000")),
                new InstallmentMetricsCalculator.ProjectedInstallment(
                        LocalDate.of(2026, 5, 5), new BigDecimal("100.0000")),
                new InstallmentMetricsCalculator.ProjectedInstallment(
                        LocalDate.of(2026, 6, 5), new BigDecimal("100.0000")));

        // 100/1.1 + 100/1.21 + 100/1.331 = 90.9090909 + 82.6446281 + 75.1314801 = 248.6852
        BigDecimal realRemaining = calculator.calculateRealRemainingAmount(
                projected, today, new BigDecimal("10.000000"));

        assertThat(realRemaining).isEqualByComparingTo("248.6852");
    }

    @Test
    void doesNotInflateCurrentMonthOrPastDueProjectedInstallments() {
        LocalDate today = LocalDate.of(2026, 4, 1);
        List<InstallmentMetricsCalculator.ProjectedInstallment> projected = List.of(
                new InstallmentMetricsCalculator.ProjectedInstallment(
                        LocalDate.of(2026, 4, 5), new BigDecimal("100.0000")),
                new InstallmentMetricsCalculator.ProjectedInstallment(
                        LocalDate.of(2026, 5, 5), new BigDecimal("100.0000")));

        // k=0 -> 100, k=1 -> 100/1.05 = 95.2381 -> sum = 195.2381
        BigDecimal realRemaining = calculator.calculateRealRemainingAmount(
                projected, today, new BigDecimal("5.000000"));

        assertThat(realRemaining).isEqualByComparingTo("195.2381");
    }

    @Test
    void returnsNullRealRemainingAmountWhenIpcIsUnavailable() {
        BigDecimal realRemaining = calculator.calculateRealRemainingAmount(
                List.of(new InstallmentMetricsCalculator.ProjectedInstallment(
                        LocalDate.of(2026, 4, 5), new BigDecimal("100.0000"))),
                LocalDate.of(2026, 3, 20),
                null);

        assertThat(realRemaining).isNull();
    }

    @Test
    void returnsZeroRealRemainingAmountWhenNoProjectedInstallmentsRemain() {
        BigDecimal realRemaining = calculator.calculateRealRemainingAmount(
                List.of(), LocalDate.of(2026, 3, 20), new BigDecimal("4.200000"));

        assertThat(realRemaining).isEqualByComparingTo("0.0000");
    }
}
