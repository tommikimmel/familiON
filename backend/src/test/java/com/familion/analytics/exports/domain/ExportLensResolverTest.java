package com.familion.analytics.exports.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.analytics.domain.AnalyticsLens;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExportLensResolverTest {

    private final ExportLensResolver resolver = new ExportLensResolver();

    @Test
    void unitMatchesAnalyticsLensConventions() {
        assertThat(resolver.unit(AnalyticsLens.NOMINAL, "ARS", "USD")).isEqualTo("ARS");
        assertThat(resolver.unit(AnalyticsLens.CONSTANT_BASE, "ARS", "USD"))
                .isEqualTo("ARS (hoy)");
        assertThat(resolver.unit(AnalyticsLens.REFERENCE_CURRENCY, "ARS", "USD")).isEqualTo("USD");
        assertThat(resolver.unit(AnalyticsLens.INCOME_PCT, "ARS", "USD")).isEqualTo("%");
    }

    @Test
    void resolveNominalUsesSnapshotBaseAmountOrFallsBackToRawAmount() {
        BigDecimal fromSnapshot = resolver.resolveTransactionAmount(
                AnalyticsLens.NOMINAL,
                new BigDecimal("100.0000"),
                new BigDecimal("120000.0000"),
                new BigDecimal("100.0000"),
                new BigDecimal("110.000000"),
                new BigDecimal("121.000000"),
                null);
        assertThat(fromSnapshot).isEqualByComparingTo("120000.0000");

        BigDecimal fallback = resolver.resolveTransactionAmount(
                AnalyticsLens.NOMINAL,
                new BigDecimal("5000.0000"),
                null,
                null,
                null,
                null,
                null);
        assertThat(fallback).isEqualByComparingTo("5000.0000");
    }

    @Test
    void resolveConstantBaseAdjustsByIpcAndFallsBackWhenIpcMissing() {
        BigDecimal adjusted = resolver.resolveTransactionAmount(
                AnalyticsLens.CONSTANT_BASE,
                new BigDecimal("50000.0000"),
                new BigDecimal("50000.0000"),
                new BigDecimal("50.0000"),
                new BigDecimal("110.000000"),
                new BigDecimal("121.000000"),
                null);
        assertThat(adjusted).isEqualByComparingTo("55000.0000");

        BigDecimal approximated = resolver.resolveTransactionAmount(
                AnalyticsLens.CONSTANT_BASE,
                new BigDecimal("50000.0000"),
                new BigDecimal("50000.0000"),
                new BigDecimal("50.0000"),
                null,
                new BigDecimal("121.000000"),
                null);
        assertThat(approximated).isEqualByComparingTo("50000.0000");
    }

    @Test
    void resolveReferenceCurrencyUsesAmountInReferenceOrFallsBackToRaw() {
        BigDecimal ref = resolver.resolveTransactionAmount(
                AnalyticsLens.REFERENCE_CURRENCY,
                new BigDecimal("100000.0000"),
                new BigDecimal("100000.0000"),
                new BigDecimal("85.5000"),
                null,
                null,
                null);
        assertThat(ref).isEqualByComparingTo("85.5000");

        BigDecimal fallback = resolver.resolveTransactionAmount(
                AnalyticsLens.REFERENCE_CURRENCY,
                new BigDecimal("50.0000"),
                null,
                null,
                null,
                null,
                null);
        assertThat(fallback).isEqualByComparingTo("50.0000");
    }

    @Test
    void resolveIncomePctComputesPercentageAndReturnsNullWhenIncomeIsZeroOrNull() {
        BigDecimal pct = resolver.resolveTransactionAmount(
                AnalyticsLens.INCOME_PCT,
                new BigDecimal("25000.0000"),
                new BigDecimal("25000.0000"),
                null,
                null,
                null,
                new BigDecimal("200000.0000"));
        assertThat(pct).isEqualByComparingTo("12.5000");

        assertThat(resolver.resolveTransactionAmount(
                        AnalyticsLens.INCOME_PCT,
                        new BigDecimal("25000.0000"),
                        new BigDecimal("25000.0000"),
                        null,
                        null,
                        null,
                        BigDecimal.ZERO))
                .isNull();
        assertThat(resolver.resolveTransactionAmount(
                        AnalyticsLens.INCOME_PCT,
                        new BigDecimal("25000.0000"),
                        new BigDecimal("25000.0000"),
                        null,
                        null,
                        null,
                        null))
                .isNull();
    }
}
