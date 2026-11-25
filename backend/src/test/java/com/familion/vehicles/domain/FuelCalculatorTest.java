package com.familion.vehicles.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FuelCalculatorTest {

    @Test
    @DisplayName("Compute missing liters from price and total amount")
    void computeMissingLiters() {
        FuelCalculator.FuelAmounts amounts = FuelCalculator.resolveAmounts(
                null,
                new BigDecimal("1000.0000"),
                new BigDecimal("45000.0000"));

        assertThat(amounts.liters()).isEqualByComparingTo(new BigDecimal("45.000"));
        assertThat(amounts.pricePerLiter()).isEqualByComparingTo(new BigDecimal("1000.0000"));
        assertThat(amounts.totalAmount()).isEqualByComparingTo(new BigDecimal("45000.0000"));
    }

    @Test
    @DisplayName("Compute missing price per liter from liters and total amount")
    void computeMissingPricePerLiter() {
        FuelCalculator.FuelAmounts amounts = FuelCalculator.resolveAmounts(
                new BigDecimal("40.000"),
                null,
                new BigDecimal("50000.0000"));

        assertThat(amounts.liters()).isEqualByComparingTo(new BigDecimal("40.000"));
        assertThat(amounts.pricePerLiter()).isEqualByComparingTo(new BigDecimal("1250.0000"));
        assertThat(amounts.totalAmount()).isEqualByComparingTo(new BigDecimal("50000.0000"));
    }

    @Test
    @DisplayName("Compute missing total amount from liters and price per liter")
    void computeMissingTotalAmount() {
        FuelCalculator.FuelAmounts amounts = FuelCalculator.resolveAmounts(
                new BigDecimal("35.500"),
                new BigDecimal("1100.5000"),
                null);

        assertThat(amounts.liters()).isEqualByComparingTo(new BigDecimal("35.500"));
        assertThat(amounts.pricePerLiter()).isEqualByComparingTo(new BigDecimal("1100.5000"));
        assertThat(amounts.totalAmount()).isEqualByComparingTo(new BigDecimal("39067.7500"));
    }

    @Test
    @DisplayName("When all 3 present, allow within 1% difference")
    void allowWithinOnePercent() {
        // 40 * 1000 = 40000. total is 40200 (diff is 200, which is 0.49% <= 1%)
        FuelCalculator.FuelAmounts amounts = FuelCalculator.resolveAmounts(
                new BigDecimal("40.000"),
                new BigDecimal("1000.0000"),
                new BigDecimal("40200.0000"));

        assertThat(amounts.totalAmount()).isEqualByComparingTo(new BigDecimal("40200.0000"));
    }

    @Test
    @DisplayName("When all 3 present and diff > 1%, throw BusinessRuleException")
    void rejectExceedingOnePercent() {
        // 40 * 1000 = 40000. total is 42000 (diff is 2000, 2000 / 42000 = 4.76% > 1%)
        assertThatThrownBy(() -> FuelCalculator.resolveAmounts(
                new BigDecimal("40.000"),
                new BigDecimal("1000.0000"),
                new BigDecimal("42000.0000")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Throw BusinessRuleException when fewer than 2 values provided")
    void rejectFewerThanTwo() {
        assertThatThrownBy(() -> FuelCalculator.resolveAmounts(
                new BigDecimal("40.000"), null, null))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> FuelCalculator.resolveAmounts(
                null, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
