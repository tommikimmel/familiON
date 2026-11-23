package com.familion.finance.transactions.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LensCalculatorTest {

    private final LensCalculator calculator = new LensCalculator();

    @Test
    void nominalUsesBaseCurrencyAmountAndUnit() {
        LensResult result = calculator.calculate(
                Lens.NOMINAL,
                "ARS",
                "USD",
                new BigDecimal("10.0000"),
                new BigDecimal("12500.0000"),
                new BigDecimal("10.0000"),
                "USD",
                new BigDecimal("200.000000"),
                new BigDecimal("220.000000"),
                null);

        assertThat(result.lens()).isEqualTo(Lens.NOMINAL);
        assertThat(result.lensAmount()).isEqualByComparingTo("12500.0000");
        assertThat(result.lensUnit()).isEqualTo("ARS");
        assertThat(result.lensApproximated()).isFalse();
    }

    @Test
    void constantBaseAdjustsByLatestOverSnapshotIpc() {
        LensResult result = calculator.calculate(
                Lens.CONSTANT_BASE,
                "ARS",
                "USD",
                new BigDecimal("10000.0000"),
                new BigDecimal("10000.0000"),
                new BigDecimal("10.0000"),
                "USD",
                new BigDecimal("200.000000"),
                new BigDecimal("250.000000"),
                null);

        assertThat(result.lens()).isEqualTo(Lens.CONSTANT_BASE);
        assertThat(result.lensAmount()).isEqualByComparingTo("12500.0000");
        assertThat(result.lensUnit()).isEqualTo("ARS");
        assertThat(result.lensApproximated()).isFalse();
    }

    @Test
    void constantBaseFallsBackToNominalAndMarksApproximatedWhenIpcMissing() {
        LensResult missingSnapshotIpc = calculator.calculate(
                Lens.CONSTANT_BASE,
                "ARS",
                "USD",
                new BigDecimal("10000.0000"),
                new BigDecimal("10000.0000"),
                new BigDecimal("10.0000"),
                "USD",
                null,
                new BigDecimal("250.000000"),
                null);

        assertThat(missingSnapshotIpc.lensAmount()).isEqualByComparingTo("10000.0000");
        assertThat(missingSnapshotIpc.lensUnit()).isEqualTo("ARS");
        assertThat(missingSnapshotIpc.lensApproximated()).isTrue();

        LensResult missingLatestIpc = calculator.calculate(
                Lens.CONSTANT_BASE,
                "ARS",
                "USD",
                new BigDecimal("10000.0000"),
                new BigDecimal("10000.0000"),
                new BigDecimal("10.0000"),
                "USD",
                new BigDecimal("200.000000"),
                null,
                null);

        assertThat(missingLatestIpc.lensAmount()).isEqualByComparingTo("10000.0000");
        assertThat(missingLatestIpc.lensApproximated()).isTrue();
    }

    @Test
    void referenceCurrencyReturnsAmountInReferenceAndReferenceUnit() {
        LensResult result = calculator.calculate(
                Lens.REFERENCE_CURRENCY,
                "ARS",
                "USD",
                new BigDecimal("12500.0000"),
                new BigDecimal("12500.0000"),
                new BigDecimal("10.0000"),
                "USD",
                new BigDecimal("200.000000"),
                new BigDecimal("250.000000"),
                null);

        assertThat(result.lens()).isEqualTo(Lens.REFERENCE_CURRENCY);
        assertThat(result.lensAmount()).isEqualByComparingTo("10.0000");
        assertThat(result.lensUnit()).isEqualTo("USD");
        assertThat(result.lensApproximated()).isFalse();
    }

    @Test
    void incomePctComputesPercentageOfMonthlyIncomeAndReturnsNullWithoutIncome() {
        LensResult withIncome = calculator.calculate(
                Lens.INCOME_PCT,
                "ARS",
                "USD",
                new BigDecimal("25000.0000"),
                new BigDecimal("25000.0000"),
                new BigDecimal("25.0000"),
                "USD",
                null,
                null,
                new BigDecimal("100000.0000"));

        assertThat(withIncome.lens()).isEqualTo(Lens.INCOME_PCT);
        assertThat(withIncome.lensAmount()).isEqualByComparingTo("25.0000");
        assertThat(withIncome.lensUnit()).isEqualTo("%");
        assertThat(withIncome.lensApproximated()).isFalse();

        LensResult withoutIncome = calculator.calculate(
                Lens.INCOME_PCT,
                "ARS",
                "USD",
                new BigDecimal("25000.0000"),
                new BigDecimal("25000.0000"),
                new BigDecimal("25.0000"),
                "USD",
                null,
                null,
                null);

        assertThat(withoutIncome.lensAmount()).isNull();
        assertThat(withoutIncome.lensUnit()).isEqualTo("%");
        assertThat(withoutIncome.lensApproximated()).isFalse();
    }
}
