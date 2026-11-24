package com.familion.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsLensCalculatorTest {

    private final AnalyticsLensCalculator calculator = new AnalyticsLensCalculator();

    @Test
    void resolvesUnitForEveryLens() {
        assertThat(calculator.unit(AnalyticsLens.NOMINAL, "ARS", "USD")).isEqualTo("ARS");
        assertThat(calculator.unit(AnalyticsLens.CONSTANT_BASE, "ARS", "USD")).isEqualTo("ARS (hoy)");
        assertThat(calculator.unit(AnalyticsLens.REFERENCE_CURRENCY, "ARS", "USD")).isEqualTo("USD");
        assertThat(calculator.unit(AnalyticsLens.INCOME_PCT, "ARS", "USD")).isEqualTo("%");
    }

    @Test
    void computesConstantBaseWithIpcRatio() {
        AnalyticsLensCalculator.ConstantBaseValue result = calculator.constantBaseAmount(
                new BigDecimal("10000.0000"),
                new BigDecimal("100.000000"),
                new BigDecimal("125.000000"));

        assertThat(result.amount()).isEqualByComparingTo("12500.0000");
        assertThat(result.amount().scale()).isEqualTo(4);
        assertThat(result.approximated()).isFalse();
    }

    @Test
    void fallsBackToNominalAndMarksApproximatedWhenIpcMissing() {
        AnalyticsLensCalculator.ConstantBaseValue missingSnapshot = calculator.constantBaseAmount(
                new BigDecimal("10000"), null, new BigDecimal("125.000000"));
        assertThat(missingSnapshot.amount()).isEqualByComparingTo("10000.0000");
        assertThat(missingSnapshot.approximated()).isTrue();

        AnalyticsLensCalculator.ConstantBaseValue missingLatest = calculator.constantBaseAmount(
                new BigDecimal("10000"), new BigDecimal("100.000000"), null);
        assertThat(missingLatest.amount()).isEqualByComparingTo("10000.0000");
        assertThat(missingLatest.approximated()).isTrue();
    }

    @Test
    void computesIncomePctAndReturnsNullWhenIncomeIsZero() {
        assertThat(calculator.incomePctAmount(new BigDecimal("25000"), new BigDecimal("100000")))
                .isEqualByComparingTo("25.0000");
        assertThat(calculator.incomePctAmount(new BigDecimal("25000"), BigDecimal.ZERO)).isNull();
        assertThat(calculator.incomePctAmount(new BigDecimal("25000"), null)).isNull();
    }

    @Test
    void computesSavingsRatePctIncludingNegativeBalance() {
        assertThat(calculator.savingsRatePct(new BigDecimal("100000"), new BigDecimal("20000")))
                .isEqualByComparingTo("20.0000");
        assertThat(calculator.savingsRatePct(new BigDecimal("100000"), new BigDecimal("-15000")))
                .isEqualByComparingTo("-15.0000");
        assertThat(calculator.savingsRatePct(BigDecimal.ZERO, new BigDecimal("-15000"))).isNull();
    }

    @Test
    void computesVariationPctAndRealVariationPctOverHistory() {
        assertThat(calculator.variationPct(new BigDecimal("120"), new BigDecimal("100")))
                .isEqualByComparingTo("20.0000");
        assertThat(calculator.variationPct(new BigDecimal("80"), new BigDecimal("100")))
                .isEqualByComparingTo("-20.0000");
        assertThat(calculator.variationPct(new BigDecimal("80"), BigDecimal.ZERO)).isNull();

        assertThat(calculator.realVariationPct(
                        new BigDecimal("150"),
                        List.of(new BigDecimal("100"), new BigDecimal("120"), new BigDecimal("80"))))
                .isEqualByComparingTo("50.0000");

        // Ignores empty periods when averaging available history
        assertThat(calculator.realVariationPct(
                        new BigDecimal("150"),
                        List.of(BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("200"))))
                .isEqualByComparingTo("0.0000");

        // Null when there is no prior history
        assertThat(calculator.realVariationPct(
                        new BigDecimal("150"), List.of(BigDecimal.ZERO, BigDecimal.ZERO)))
                .isNull();
        assertThat(calculator.realVariationPct(new BigDecimal("150"), List.of())).isNull();
    }
}
