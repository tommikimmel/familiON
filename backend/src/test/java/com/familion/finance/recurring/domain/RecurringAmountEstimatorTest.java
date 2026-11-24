package com.familion.finance.recurring.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.familion.economics.application.api.EconomicsApi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecurringAmountEstimatorTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);
    private static final YearMonth TARGET_MONTH = YearMonth.of(2026, 6);
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);

    @Mock
    private EconomicsApi economicsApi;

    private RecurringAmountEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new RecurringAmountEstimator();
    }

    @Test
    void manualReturnsBaseAmountAtScale4WithoutCallingEconomics() {
        BigDecimal estimated = estimator.estimate(
                AdjustmentRule.MANUAL,
                new BigDecimal("150000.5"),
                "ARS",
                START_DATE,
                TARGET_MONTH,
                TODAY,
                "USD",
                "USD_MEP",
                new BigDecimal("200000"),
                economicsApi);

        assertThat(estimated).isEqualByComparingTo("150000.5000");
        assertThat(estimated.scale()).isEqualTo(4);
        verifyNoInteractions(economicsApi);
    }

    @Test
    void ipcMultipliesBaseAmountByAccumulatedIpcRatio() {
        when(economicsApi.valueAt("IPC_ACUMULADO", START_DATE))
                .thenReturn(Optional.of(new BigDecimal("100.000000")));
        when(economicsApi.valueAt("IPC_ACUMULADO", TARGET_MONTH.atEndOfMonth()))
                .thenReturn(Optional.of(new BigDecimal("125.500000")));

        BigDecimal estimated = estimator.estimate(
                AdjustmentRule.IPC,
                new BigDecimal("200000.0000"),
                "ARS",
                START_DATE,
                TARGET_MONTH,
                TODAY,
                "USD",
                "USD_MEP",
                null,
                economicsApi);

        assertThat(estimated).isEqualByComparingTo("251000.0000");
        assertThat(estimated.scale()).isEqualTo(4);
    }

    @Test
    void ipcFallsBackToLatestAccumulatedIpcWhenTargetMonthHasNoPriorData() {
        when(economicsApi.valueAt("IPC_ACUMULADO", START_DATE))
                .thenReturn(Optional.of(new BigDecimal("100.000000")));
        when(economicsApi.valueAt("IPC_ACUMULADO", TARGET_MONTH.atEndOfMonth()))
                .thenReturn(Optional.empty());
        when(economicsApi.latestValue("IPC_ACUMULADO"))
                .thenReturn(Optional.of(new BigDecimal("130.000000")));

        BigDecimal estimated = estimator.estimate(
                AdjustmentRule.IPC,
                new BigDecimal("100000.0000"),
                "ARS",
                START_DATE,
                TARGET_MONTH,
                TODAY,
                "USD",
                "USD_MEP",
                null,
                economicsApi);

        assertThat(estimated).isEqualByComparingTo("130000.0000");
    }

    @Test
    void ipcFallsBackToBaseAmountWhenStartIpcIsMissingOrNonPositive() {
        when(economicsApi.valueAt("IPC_ACUMULADO", START_DATE)).thenReturn(Optional.empty());

        BigDecimal estimated = estimator.estimate(
                AdjustmentRule.IPC,
                new BigDecimal("85000.0000"),
                "ARS",
                START_DATE,
                TARGET_MONTH,
                TODAY,
                "USD",
                "USD_MEP",
                null,
                economicsApi);

        assertThat(estimated).isEqualByComparingTo("85000.0000");
    }

    @Test
    void ipcUsesHalfEvenRoundingToScale4() {
        when(economicsApi.valueAt("IPC_ACUMULADO", START_DATE))
                .thenReturn(Optional.of(new BigDecimal("3.000000")));
        when(economicsApi.valueAt("IPC_ACUMULADO", TARGET_MONTH.atEndOfMonth()))
                .thenReturn(Optional.of(new BigDecimal("10.000000")));

        BigDecimal estimated = estimator.estimate(
                AdjustmentRule.IPC,
                new BigDecimal("100.0000"),
                "ARS",
                START_DATE,
                TARGET_MONTH,
                TODAY,
                "USD",
                "USD_MEP",
                null,
                economicsApi);

        assertThat(estimated).isEqualByComparingTo("333.3333");
        assertThat(estimated.scale()).isEqualTo(4);
    }

    @Test
    void referenceCurrencyConvertsBaseAmountFromReferenceCurrencyUsingTodayRate() {
        when(economicsApi.convert(
                        new BigDecimal("500.0000"), "USD", "ARS", TODAY, "USD_MEP"))
                .thenReturn(new BigDecimal("600000.0000"));

        BigDecimal estimated = estimator.estimate(
                AdjustmentRule.REFERENCE_CURRENCY,
                new BigDecimal("500.0000"),
                "ARS",
                START_DATE,
                TARGET_MONTH,
                TODAY,
                "USD",
                "USD_MEP",
                null,
                economicsApi);

        assertThat(estimated).isEqualByComparingTo("600000.0000");
        verify(economicsApi)
                .convert(new BigDecimal("500.0000"), "USD", "ARS", TODAY, "USD_MEP");
    }

    @Test
    void lastPaidAppliesLatestMonthlyIpcPercentage() {
        when(economicsApi.latestValue("IPC_MENSUAL"))
                .thenReturn(Optional.of(new BigDecimal("3.750000")));

        BigDecimal estimated = estimator.estimate(
                AdjustmentRule.LAST_PAID,
                new BigDecimal("100000.0000"),
                "ARS",
                START_DATE,
                TARGET_MONTH,
                TODAY,
                "USD",
                "USD_MEP",
                new BigDecimal("120000.0000"),
                economicsApi);

        // 120000 * 1.0375 = 124500.0000
        assertThat(estimated).isEqualByComparingTo("124500.0000");
        assertThat(estimated.scale()).isEqualTo(4);
    }

    @Test
    void lastPaidFallsBackToBaseAmountWhenNoPreviousPaymentExists() {
        BigDecimal estimated = estimator.estimate(
                AdjustmentRule.LAST_PAID,
                new BigDecimal("95000.0000"),
                "ARS",
                START_DATE,
                TARGET_MONTH,
                TODAY,
                "USD",
                "USD_MEP",
                null,
                economicsApi);

        assertThat(estimated).isEqualByComparingTo("95000.0000");
        verifyNoInteractions(economicsApi);
    }

    @Test
    void lastPaidReturnsLastPaidAmountWhenMonthlyIpcIsMissing() {
        when(economicsApi.latestValue("IPC_MENSUAL")).thenReturn(Optional.empty());

        BigDecimal estimated = estimator.estimate(
                AdjustmentRule.LAST_PAID,
                new BigDecimal("95000.0000"),
                "ARS",
                START_DATE,
                TARGET_MONTH,
                TODAY,
                "USD",
                "USD_MEP",
                new BigDecimal("112345.6789"),
                economicsApi);

        assertThat(estimated).isEqualByComparingTo("112345.6789");
    }
}
