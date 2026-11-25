package com.familion.analytics.exports.domain;

import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.domain.AnalyticsLensCalculator;
import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.util.Objects;

public final class ExportLensResolver {

    private final AnalyticsLensCalculator lensCalculator;

    public ExportLensResolver() {
        this.lensCalculator = new AnalyticsLensCalculator();
    }

    public String unit(AnalyticsLens lens, String baseCurrency, String referenceCurrency) {
        return lensCalculator.unit(lens, baseCurrency, referenceCurrency);
    }

    public BigDecimal resolveTransactionAmount(
            AnalyticsLens lens,
            BigDecimal rawAmount,
            BigDecimal snapshotBaseCurrencyAmount,
            BigDecimal snapshotAmountInReference,
            BigDecimal snapshotIpcIndex,
            BigDecimal latestIpcIndex,
            BigDecimal monthlyNominalIncome) {
        Objects.requireNonNull(lens, "lens must not be null");
        Objects.requireNonNull(rawAmount, "rawAmount must not be null");

        BigDecimal baseAmount =
                snapshotBaseCurrencyAmount != null ? snapshotBaseCurrencyAmount : rawAmount;

        return switch (lens) {
            case NOMINAL -> Money.scale(baseAmount);
            case CONSTANT_BASE -> lensCalculator
                    .constantBaseAmount(baseAmount, snapshotIpcIndex, latestIpcIndex)
                    .amount();
            case REFERENCE_CURRENCY -> Money.scale(
                    snapshotAmountInReference != null ? snapshotAmountInReference : rawAmount);
            case INCOME_PCT -> lensCalculator.incomePctAmount(baseAmount, monthlyNominalIncome);
        };
    }
}
