package com.familion.finance.exchanges.domain;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure domain rules for classifying currency exchanges (BUY/SELL/SWAP), computing the implicit
 * exchange rate, and validating an optional user-supplied applied exchange rate within 0.5%.
 */
public final class ExchangeRateCalculator {

    private static final BigDecimal MAX_RATE_DEVIATION_RATIO = new BigDecimal("0.005");

    public record RateCalculation(ExchangeOperation operation, BigDecimal appliedExchangeRate) {}

    public ExchangeOperation determineOperation(
            String sourceCurrency, String targetCurrency, String baseCurrency) {
        String source = normalizeCurrency(sourceCurrency, "sourceCurrency");
        String target = normalizeCurrency(targetCurrency, "targetCurrency");
        String base = normalizeCurrency(baseCurrency, "baseCurrency");

        if (source.equals(target)) {
            throw new BusinessRuleException(
                    "Source and target accounts must have different currencies");
        }
        if (source.equals(base)) {
            return ExchangeOperation.BUY;
        }
        if (target.equals(base)) {
            return ExchangeOperation.SELL;
        }
        return ExchangeOperation.SWAP;
    }

    public RateCalculation calculateAndValidate(
            String sourceCurrency,
            String targetCurrency,
            String baseCurrency,
            BigDecimal sourceAmount,
            BigDecimal targetAmount,
            BigDecimal providedRate) {
        Objects.requireNonNull(sourceAmount, "sourceAmount must not be null");
        Objects.requireNonNull(targetAmount, "targetAmount must not be null");

        if (sourceAmount.compareTo(BigDecimal.ZERO) <= 0
                || targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Exchange amounts must be greater than zero");
        }

        ExchangeOperation operation =
                determineOperation(sourceCurrency, targetCurrency, baseCurrency);

        BigDecimal numerator;
        BigDecimal denominator;
        if (operation == ExchangeOperation.SELL) {
            numerator = targetAmount;
            denominator = sourceAmount;
        } else {
            // BUY: base (source) per 1 unit of foreign (target)
            // SWAP: source units per 1 unit of target
            numerator = sourceAmount;
            denominator = targetAmount;
        }

        if (providedRate != null) {
            if (providedRate.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException("Applied exchange rate must be greater than zero");
            }
            // Exact tolerance check without intermediate division rounding:
            // |providedRate - numerator / denominator| > 0.005 * (numerator / denominator)
            // <=> |providedRate * denominator - numerator| > 0.005 * numerator
            BigDecimal diff = providedRate.multiply(denominator).subtract(numerator).abs();
            BigDecimal maxAllowedDiff = numerator.multiply(MAX_RATE_DEVIATION_RATIO);
            if (diff.compareTo(maxAllowedDiff) > 0) {
                throw new BusinessRuleException(
                        "Applied exchange rate differs by more than 0.5% from the implicit rate");
            }
            BigDecimal scaledProvided = Money.rate(providedRate);
            if (scaledProvided.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException("Applied exchange rate must be greater than zero");
            }
            return new RateCalculation(operation, scaledProvided);
        }

        BigDecimal computedRate =
                numerator.divide(denominator, Money.RATE_SCALE, RoundingMode.HALF_EVEN);
        if (computedRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Computed exchange rate must be greater than zero");
        }
        return new RateCalculation(operation, computedRate);
    }

    private static String normalizeCurrency(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException(fieldName + " is required");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
