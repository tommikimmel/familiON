package com.familion.finance.recurring.domain;

import com.familion.common.money.Money;
import com.familion.economics.application.api.EconomicsApi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure domain calculator for recurring expense estimated monthly amounts according to the
 * configured {@link AdjustmentRule}.
 *
 * <p>All results are returned at scale 4 ({@link Money#SCALE}) using {@link
 * java.math.RoundingMode#HALF_EVEN}.
 */
public final class RecurringAmountEstimator {

    public static final String IPC_ACUMULADO = "IPC_ACUMULADO";
    public static final String IPC_MENSUAL = "IPC_MENSUAL";

    public BigDecimal estimate(
            AdjustmentRule rule,
            BigDecimal baseAmount,
            String currencyCode,
            LocalDate startDate,
            YearMonth targetMonth,
            LocalDate today,
            String referenceCurrency,
            String referenceRateSource,
            BigDecimal lastPaidAmount,
            EconomicsApi economicsApi) {
        Objects.requireNonNull(rule, "rule must not be null");
        Objects.requireNonNull(baseAmount, "baseAmount must not be null");
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(targetMonth, "targetMonth must not be null");
        Objects.requireNonNull(today, "today must not be null");
        Objects.requireNonNull(economicsApi, "economicsApi must not be null");

        return switch (rule) {
            case MANUAL -> Money.scale(baseAmount);
            case IPC -> estimateByIpc(baseAmount, startDate, targetMonth, economicsApi);
            case REFERENCE_CURRENCY -> estimateByReferenceCurrency(
                    baseAmount, currencyCode, today, referenceCurrency, referenceRateSource, economicsApi);
            case LAST_PAID -> estimateByLastPaid(baseAmount, lastPaidAmount, economicsApi);
        };
    }

    private BigDecimal estimateByIpc(
            BigDecimal baseAmount,
            LocalDate startDate,
            YearMonth targetMonth,
            EconomicsApi economicsApi) {
        Optional<BigDecimal> startIpc = economicsApi.valueAt(IPC_ACUMULADO, startDate);
        if (startIpc.isEmpty() || startIpc.get().compareTo(BigDecimal.ZERO) <= 0) {
            return Money.scale(baseAmount);
        }

        Optional<BigDecimal> targetIpc = economicsApi
                .valueAt(IPC_ACUMULADO, targetMonth.atEndOfMonth())
                .or(() -> economicsApi.latestValue(IPC_ACUMULADO));
        if (targetIpc.isEmpty() || targetIpc.get().compareTo(BigDecimal.ZERO) <= 0) {
            return Money.scale(baseAmount);
        }

        return Money.divide(baseAmount.multiply(targetIpc.get()), startIpc.get());
    }

    private BigDecimal estimateByReferenceCurrency(
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

    private BigDecimal estimateByLastPaid(
            BigDecimal baseAmount, BigDecimal lastPaidAmount, EconomicsApi economicsApi) {
        if (lastPaidAmount == null) {
            return Money.scale(baseAmount);
        }
        Optional<BigDecimal> latestMonthlyIpc = economicsApi.latestValue(IPC_MENSUAL);
        if (latestMonthlyIpc.isEmpty()) {
            return Money.scale(lastPaidAmount);
        }
        BigDecimal factor = BigDecimal.ONE.add(latestMonthlyIpc.get().movePointLeft(2));
        if (factor.compareTo(BigDecimal.ZERO) <= 0) {
            return Money.scale(lastPaidAmount);
        }
        return Money.scale(lastPaidAmount.multiply(factor));
    }
}
