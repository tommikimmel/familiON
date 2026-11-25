package com.familion.vehicles.domain;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FuelCalculator {

    public static final int LITERS_SCALE = 3;
    public static final int PRICE_SCALE = 4;
    public static final int TOTAL_SCALE = 4;
    private static final BigDecimal ONE_PERCENT = new BigDecimal("0.01");

    private FuelCalculator() {}

    public static FuelAmounts resolveAmounts(
            BigDecimal liters, BigDecimal pricePerLiter, BigDecimal totalAmount) {
        int presentCount = (liters != null ? 1 : 0)
                + (pricePerLiter != null ? 1 : 0)
                + (totalAmount != null ? 1 : 0);

        if (presentCount < 2) {
            throw new BusinessRuleException(
                    "At least 2 of liters, pricePerLiter, and totalAmount are required");
        }

        if (liters != null && liters.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Liters must be greater than zero");
        }
        if (pricePerLiter != null && pricePerLiter.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Price per liter must be greater than zero");
        }
        if (totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Total amount must be greater than zero");
        }

        if (liters == null) {
            BigDecimal computedLiters = totalAmount.divide(pricePerLiter, LITERS_SCALE, RoundingMode.HALF_EVEN);
            return new FuelAmounts(
                    computedLiters,
                    pricePerLiter.setScale(PRICE_SCALE, RoundingMode.HALF_EVEN),
                    totalAmount.setScale(TOTAL_SCALE, RoundingMode.HALF_EVEN));
        }

        if (pricePerLiter == null) {
            BigDecimal computedPrice = totalAmount.divide(liters, PRICE_SCALE, RoundingMode.HALF_EVEN);
            return new FuelAmounts(
                    liters.setScale(LITERS_SCALE, RoundingMode.HALF_EVEN),
                    computedPrice,
                    totalAmount.setScale(TOTAL_SCALE, RoundingMode.HALF_EVEN));
        }

        if (totalAmount == null) {
            BigDecimal computedTotal = liters.multiply(pricePerLiter).setScale(TOTAL_SCALE, RoundingMode.HALF_EVEN);
            return new FuelAmounts(
                    liters.setScale(LITERS_SCALE, RoundingMode.HALF_EVEN),
                    pricePerLiter.setScale(PRICE_SCALE, RoundingMode.HALF_EVEN),
                    computedTotal);
        }

        // All 3 present: verify difference <= 1% of totalAmount
        BigDecimal product = liters.multiply(pricePerLiter);
        BigDecimal diff = product.subtract(totalAmount).abs();
        BigDecimal threshold = totalAmount.multiply(ONE_PERCENT);
        if (diff.compareTo(threshold) > 0) {
            throw new BusinessRuleException(
                    "Liters multiplied by pricePerLiter differs from totalAmount by more than 1%");
        }

        return new FuelAmounts(
                liters.setScale(LITERS_SCALE, RoundingMode.HALF_EVEN),
                pricePerLiter.setScale(PRICE_SCALE, RoundingMode.HALF_EVEN),
                totalAmount.setScale(TOTAL_SCALE, RoundingMode.HALF_EVEN));
    }

    public record FuelAmounts(
            BigDecimal liters, BigDecimal pricePerLiter, BigDecimal totalAmount) {}
}
