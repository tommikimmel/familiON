package com.familion.family.allowances.domain;

import com.familion.common.money.Money;
import com.familion.economics.application.api.EconomicsApi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure domain calculator for the current allowance amount according to the configured
 * {@link AllowanceAdjustmentRule}.
 */
public final class AllowanceAmountCalculator {

    public static final String IPC_ACUMULADO = "IPC_ACUMULADO";

    public BigDecimal calculateCurrentAmount(
            AllowanceAdjustmentRule rule,
            BigDecimal baseAmount,
            String currencyCode,
            LocalDate createdDate,
            LocalDate today,
            String referenceCurrency,
            String referenceRateSource,
            EconomicsApi economicsApi) {
        Objects.requireNonNull(rule, "rule must not be null");
        Objects.requireNonNull(baseAmount, "baseAmount must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Objects.requireNonNull(createdDate, "createdDate must not be null");
        Objects.requireNonNull(today, "today must not be null");
        Objects.requireNonNull(economicsApi, "economicsApi must not be null");

        return switch (rule) {
            case MANUAL -> Money.scale(baseAmount);
            case IPC -> calculateByIpc(baseAmount, createdDate, economicsApi);
            case REFERENCE_CURRENCY -> calculateByReferenceCurrency(
                    baseAmount, currencyCode, today, referenceCurrency, referenceRateSource, economicsApi);
        };
    }

    private BigDecimal calculateByIpc(
            BigDecimal baseAmount, LocalDate createdDate, EconomicsApi economicsApi) {
        Optional<BigDecimal> baseIpc = economicsApi.valueAt(IPC_ACUMULADO, createdDate);
        if (baseIpc.isEmpty() || baseIpc.get().compareTo(BigDecimal.ZERO) <= 0) {
            return Money.scale(baseAmount);
        }

        Optional<BigDecimal> latestIpc = economicsApi.latestValue(IPC_ACUMULADO);
        if (latestIpc.isEmpty() || latestIpc.get().compareTo(BigDecimal.ZERO) <= 0) {
            return Money.scale(baseAmount);
        }

        return Money.divide(baseAmount.multiply(latestIpc.get()), baseIpc.get());
    }

    private BigDecimal calculateByReferenceCurrency(
            BigDecimal baseAmount,
            String currencyCode,
            LocalDate today,
            String referenceCurrency,
            String referenceRateSource,
            EconomicsApi economicsApi) {
        Objects.requireNonNull(referenceCurrency, "referenceCurrency must not be null");
        BigDecimal converted = economicsApi.convert(
                baseAmount, referenceCurrency, currencyCode, today, referenceRateSource);
        return Money.scale(converted);
    }
}
