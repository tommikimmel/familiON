package com.familion.finance.exchanges.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExchangeRateCalculatorTest {

    private final ExchangeRateCalculator calculator = new ExchangeRateCalculator();

    @Test
    void computesBuyRateAsSourceOverTargetWithScaleSix() {
        ExchangeRateCalculator.RateCalculation result = calculator.calculateAndValidate(
                "ARS",
                "USD",
                "ARS",
                new BigDecimal("1185000.0000"),
                new BigDecimal("1000.0000"),
                null);

        assertThat(result.operation()).isEqualTo(ExchangeOperation.BUY);
        assertThat(result.appliedExchangeRate()).isEqualByComparingTo("1185.000000");
        assertThat(result.appliedExchangeRate().scale()).isEqualTo(6);
    }

    @Test
    void computesSellRateAsTargetOverSourceWithScaleSix() {
        ExchangeRateCalculator.RateCalculation result = calculator.calculateAndValidate(
                "USD",
                "ARS",
                "ARS",
                new BigDecimal("200.0000"),
                new BigDecimal("250000.0000"),
                null);

        assertThat(result.operation()).isEqualTo(ExchangeOperation.SELL);
        assertThat(result.appliedExchangeRate()).isEqualByComparingTo("1250.000000");
        assertThat(result.appliedExchangeRate().scale()).isEqualTo(6);
    }

    @Test
    void computesSwapRateAsSourceOverTargetWithScaleSix() {
        ExchangeRateCalculator.RateCalculation result = calculator.calculateAndValidate(
                "USD",
                "EUR",
                "ARS",
                new BigDecimal("1000.0000"),
                new BigDecimal("920.0000"),
                null);

        assertThat(result.operation()).isEqualTo(ExchangeOperation.SWAP);
        assertThat(result.appliedExchangeRate()).isEqualByComparingTo("1.086957");
        assertThat(result.appliedExchangeRate().scale()).isEqualTo(6);
    }

    @Test
    void acceptsProvidedRateWithinHalfPercentTolerance() {
        // Implicit rate = 1185; +0.5% = 1190.925, -0.5% = 1179.075
        ExchangeRateCalculator.RateCalculation upperBoundary = calculator.calculateAndValidate(
                "ARS",
                "USD",
                "ARS",
                new BigDecimal("1185000"),
                new BigDecimal("1000"),
                new BigDecimal("1190.925"));

        assertThat(upperBoundary.appliedExchangeRate()).isEqualByComparingTo("1190.925000");
        assertThat(upperBoundary.appliedExchangeRate().scale()).isEqualTo(6);

        ExchangeRateCalculator.RateCalculation lowerBoundary = calculator.calculateAndValidate(
                "ARS",
                "USD",
                "ARS",
                new BigDecimal("1185000"),
                new BigDecimal("1000"),
                new BigDecimal("1179.075"));

        assertThat(lowerBoundary.appliedExchangeRate()).isEqualByComparingTo("1179.075000");
    }

    @Test
    void rejectsProvidedRateOutsideHalfPercentTolerance() {
        assertThatThrownBy(() -> calculator.calculateAndValidate(
                        "ARS",
                        "USD",
                        "ARS",
                        new BigDecimal("1185000"),
                        new BigDecimal("1000"),
                        new BigDecimal("1191.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("0.5%");

        assertThatThrownBy(() -> calculator.calculateAndValidate(
                        "USD",
                        "ARS",
                        "ARS",
                        new BigDecimal("200"),
                        new BigDecimal("250000"),
                        new BigDecimal("1240.00")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("0.5%");
    }

    @Test
    void rejectsSameSourceAndTargetCurrency() {
        assertThatThrownBy(() -> calculator.calculateAndValidate(
                        "USD",
                        "usd",
                        "ARS",
                        new BigDecimal("100"),
                        new BigDecimal("100"),
                        null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different currencies");
    }

    @Test
    void rejectsNonPositiveAmountsOrProvidedRate() {
        assertThatThrownBy(() -> calculator.calculateAndValidate(
                        "ARS", "USD", "ARS", BigDecimal.ZERO, new BigDecimal("100"), null))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> calculator.calculateAndValidate(
                        "ARS",
                        "USD",
                        "ARS",
                        new BigDecimal("100000"),
                        new BigDecimal("100"),
                        BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }
}
