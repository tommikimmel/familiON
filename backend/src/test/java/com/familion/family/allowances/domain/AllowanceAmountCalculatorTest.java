package com.familion.family.allowances.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.familion.economics.application.api.EconomicsApi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AllowanceAmountCalculatorTest {

    private final AllowanceAmountCalculator calculator = new AllowanceAmountCalculator();

    @Test
    void manualReturnsScaledBaseAmountWithoutCallingEconomicsApi() {
        EconomicsApi economicsApi = mock(EconomicsApi.class);

        BigDecimal result = calculator.calculateCurrentAmount(
                AllowanceAdjustmentRule.MANUAL,
                new BigDecimal("50000"),
                "ARS",
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 11, 15),
                "USD",
                "USD_MEP",
                economicsApi);

        assertThat(result).isEqualByComparingTo("50000.0000");
        assertThat(result.scale()).isEqualTo(4);
        verifyNoInteractions(economicsApi);
    }

    @Test
    void ipcMultipliesBaseAmountByLatestOverCreatedAtIpcAcumulado() {
        EconomicsApi economicsApi = mock(EconomicsApi.class);
        LocalDate createdDate = LocalDate.of(2026, 1, 10);
        when(economicsApi.valueAt("IPC_ACUMULADO", createdDate))
                .thenReturn(Optional.of(new BigDecimal("100.000000")));
        when(economicsApi.latestValue("IPC_ACUMULADO"))
                .thenReturn(Optional.of(new BigDecimal("135.500000")));

        BigDecimal result = calculator.calculateCurrentAmount(
                AllowanceAdjustmentRule.IPC,
                new BigDecimal("50000.0000"),
                "ARS",
                createdDate,
                LocalDate.of(2026, 11, 15),
                "USD",
                "USD_MEP",
                economicsApi);

        assertThat(result).isEqualByComparingTo("67750.0000");
        assertThat(result.scale()).isEqualTo(4);
    }

    @Test
    void ipcFallsBackToBaseAmountWhenIndicesMissingOrNonPositive() {
        EconomicsApi economicsApi = mock(EconomicsApi.class);
        LocalDate createdDate = LocalDate.of(2026, 1, 10);
        when(economicsApi.valueAt("IPC_ACUMULADO", createdDate)).thenReturn(Optional.empty());

        BigDecimal missingBase = calculator.calculateCurrentAmount(
                AllowanceAdjustmentRule.IPC,
                new BigDecimal("50000"),
                "ARS",
                createdDate,
                LocalDate.of(2026, 11, 15),
                "USD",
                "USD_MEP",
                economicsApi);
        assertThat(missingBase).isEqualByComparingTo("50000.0000");

        when(economicsApi.valueAt("IPC_ACUMULADO", createdDate))
                .thenReturn(Optional.of(new BigDecimal("100.000000")));
        when(economicsApi.latestValue("IPC_ACUMULADO")).thenReturn(Optional.empty());

        BigDecimal missingLatest = calculator.calculateCurrentAmount(
                AllowanceAdjustmentRule.IPC,
                new BigDecimal("50000"),
                "ARS",
                createdDate,
                LocalDate.of(2026, 11, 15),
                "USD",
                "USD_MEP",
                economicsApi);
        assertThat(missingLatest).isEqualByComparingTo("50000.0000");
    }

    @Test
    void referenceCurrencyConvertsFromFamilyReferenceCurrencyToAllowanceCurrency() {
        EconomicsApi economicsApi = mock(EconomicsApi.class);
        LocalDate today = LocalDate.of(2026, 11, 15);
        when(economicsApi.convert(
                        new BigDecimal("40.0000"), "USD", "ARS", today, "USD_MEP"))
                .thenReturn(new BigDecimal("50000.0000"));

        BigDecimal result = calculator.calculateCurrentAmount(
                AllowanceAdjustmentRule.REFERENCE_CURRENCY,
                new BigDecimal("40.0000"),
                "ARS",
                LocalDate.of(2026, 1, 10),
                today,
                "USD",
                "USD_MEP",
                economicsApi);

        assertThat(result).isEqualByComparingTo("50000.0000");
        assertThat(result.scale()).isEqualTo(4);
    }
}
