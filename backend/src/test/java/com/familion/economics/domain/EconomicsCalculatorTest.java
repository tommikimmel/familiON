package com.familion.economics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.economics.application.api.EconomicSnapshotData;
import com.familion.economics.application.api.FamilyEconomicContext;
import com.familion.economics.domain.EconomicsCalculator.IndexLookup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EconomicsCalculatorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private final EconomicsCalculator calculator = new EconomicsCalculator();

    @Test
    void computesSnapshotForArsTransactionWithUsdMepReference() {
        InMemoryLookup lookup = new InMemoryLookup()
                .atDate(IndexCode.USD_MEP, new BigDecimal("1200.000000"))
                .atDate(IndexCode.IPC_ACUMULADO, new BigDecimal("107.686500"))
                .atDate(IndexCode.UVA, new BigDecimal("2205.170000"));
        FamilyEconomicContext ctx = new FamilyEconomicContext("ARS", "USD", "USD_MEP");

        EconomicSnapshotData snapshot =
                calculator.snapshotFor(ctx, DATE, new BigDecimal("150000"), "ARS", lookup);

        assertThat(snapshot.referenceCurrency()).isEqualTo("USD");
        assertThat(snapshot.referenceRateSource()).isEqualTo("USD_MEP");
        assertThat(snapshot.referenceExchangeRate()).isEqualTo(new BigDecimal("1200.000000"));
        assertThat(snapshot.baseCurrencyAmount()).isEqualTo(new BigDecimal("150000.0000"));
        assertThat(snapshot.amountInReference()).isEqualTo(new BigDecimal("125.0000"));
        assertThat(snapshot.ipcIndexValue()).isEqualTo(new BigDecimal("107.686500"));
        assertThat(snapshot.uvaValue()).isEqualTo(new BigDecimal("2205.170000"));
        assertThat(snapshot.snapshotDate()).isEqualTo(DATE);
    }

    @Test
    void computesSnapshotForUsdTransactionWithArsBase() {
        InMemoryLookup lookup = new InMemoryLookup()
                .atDate(IndexCode.USD_MEP, new BigDecimal("1250.500000"));
        FamilyEconomicContext ctx = new FamilyEconomicContext("ARS", "USD", "USD_MEP");

        EconomicSnapshotData snapshot =
                calculator.snapshotFor(ctx, DATE, new BigDecimal("100"), "USD", lookup);

        assertThat(snapshot.baseCurrencyAmount()).isEqualTo(new BigDecimal("125050.0000"));
        assertThat(snapshot.referenceExchangeRate()).isEqualTo(new BigDecimal("1250.500000"));
        assertThat(snapshot.amountInReference()).isEqualTo(new BigDecimal("100.0000"));
        assertThat(snapshot.ipcIndexValue()).isNull();
        assertThat(snapshot.uvaValue()).isNull();
    }

    @Test
    void computesSnapshotWithUvaReferenceAndUsdTransactionUsesUsdMep() {
        InMemoryLookup lookup = new InMemoryLookup()
                .atDate(IndexCode.USD_MEP, new BigDecimal("1500.000000"))
                .atDate(IndexCode.UVA, new BigDecimal("2000.000000"));
        FamilyEconomicContext ctx = new FamilyEconomicContext("ARS", "UVA", "UVA");

        EconomicSnapshotData snapshot =
                calculator.snapshotFor(ctx, DATE, new BigDecimal("200"), "USD", lookup);

        // 200 USD * 1500 ARS/USD = 300000 ARS; 300000 / 2000 UVA = 150 UVA
        assertThat(snapshot.baseCurrencyAmount()).isEqualTo(new BigDecimal("300000.0000"));
        assertThat(snapshot.referenceExchangeRate()).isEqualTo(new BigDecimal("2000.000000"));
        assertThat(snapshot.amountInReference()).isEqualTo(new BigDecimal("150.0000"));
        assertThat(snapshot.uvaValue()).isEqualTo(new BigDecimal("2000.000000"));
    }

    @Test
    void computesSnapshotWhenReferenceEqualsBaseCurrency() {
        InMemoryLookup lookup = new InMemoryLookup()
                .atDate(IndexCode.USD_MEP, new BigDecimal("1200.000000"));
        FamilyEconomicContext ctx = new FamilyEconomicContext("ARS", "ARS", "USD_MEP");

        EconomicSnapshotData snapshot =
                calculator.snapshotFor(ctx, DATE, new BigDecimal("50000.125"), "ARS", lookup);

        assertThat(snapshot.referenceExchangeRate()).isEqualTo(new BigDecimal("1.000000"));
        assertThat(snapshot.baseCurrencyAmount()).isEqualTo(new BigDecimal("50000.1250"));
        assertThat(snapshot.amountInReference()).isEqualTo(new BigDecimal("50000.1250"));
    }

    @Test
    void fallsBackToLatestValueWhenDateHasNoDataThenToOneWhenDatabaseIsEmpty() {
        InMemoryLookup latestOnly = new InMemoryLookup()
                .latest(IndexCode.USD_BLUE, new BigDecimal("1675.000000"));
        FamilyEconomicContext ctx = new FamilyEconomicContext("ARS", "USD", "USD_BLUE");

        EconomicSnapshotData fallbackToLatest =
                calculator.snapshotFor(ctx, DATE, new BigDecimal("16750"), "ARS", latestOnly);
        assertThat(fallbackToLatest.referenceExchangeRate())
                .isEqualTo(new BigDecimal("1675.000000"));
        assertThat(fallbackToLatest.amountInReference()).isEqualTo(new BigDecimal("10.0000"));

        InMemoryLookup emptyLookup = new InMemoryLookup();
        EconomicSnapshotData fallbackToOne =
                calculator.snapshotFor(ctx, DATE, new BigDecimal("16750"), "ARS", emptyLookup);
        assertThat(fallbackToOne.referenceExchangeRate()).isEqualTo(new BigDecimal("1.000000"));
        assertThat(fallbackToOne.amountInReference()).isEqualTo(new BigDecimal("16750.0000"));
    }

    @Test
    void appliesHalfEvenPrecisionAndRoundingOnDivisionAndConversion() {
        // 1 / 3 = 0.333333... -> 0.3333; 0.00005 -> 0.0000 (even), 0.00015 -> 0.0002 (even)
        InMemoryLookup lookup = new InMemoryLookup()
                .atDate(IndexCode.USD_MEP, new BigDecimal("3.000000"))
                .atDate(IndexCode.EUR_OFICIAL, new BigDecimal("2.000000"));
        FamilyEconomicContext ctx = new FamilyEconomicContext("ARS", "USD", "USD_MEP");

        EconomicSnapshotData thirds =
                calculator.snapshotFor(ctx, DATE, new BigDecimal("1"), "ARS", lookup);
        assertThat(thirds.amountInReference()).isEqualTo(new BigDecimal("0.3333"));

        InMemoryLookup tieLookup = new InMemoryLookup()
                .atDate(IndexCode.USD_MEP, new BigDecimal("2.000000"));
        // 0.0001 / 2 = 0.00005 -> rounds to 0.0000 (HALF_EVEN)
        assertThat(calculator.convert(new BigDecimal("0.0001"), "ARS", "USD", DATE, "USD_MEP", tieLookup))
                .isEqualTo(new BigDecimal("0.0000"));
        // 0.0003 / 2 = 0.00015 -> rounds to 0.0002 (HALF_EVEN)
        assertThat(calculator.convert(new BigDecimal("0.0003"), "ARS", "USD", DATE, "USD_MEP", tieLookup))
                .isEqualTo(new BigDecimal("0.0002"));

        // Cross-currency conversion via base: 100 USD -> 300 ARS -> 150 EUR
        assertThat(calculator.convert(new BigDecimal("100"), "USD", "EUR", DATE, "USD_MEP", lookup))
                .isEqualTo(new BigDecimal("150.0000"));

        // USDT always maps to USD_MEP even if rateSource is USD_BLUE
        assertThat(calculator.convert(new BigDecimal("10"), "USDT", "ARS", DATE, "USD_BLUE", lookup))
                .isEqualTo(new BigDecimal("30.0000"));

        // Same currency short-circuits without lookup
        assertThat(calculator.convert(new BigDecimal("42.5"), "EUR", "EUR", DATE, "USD_MEP", new InMemoryLookup()))
                .isEqualTo(new BigDecimal("42.5000"));
    }

    private static final class InMemoryLookup implements IndexLookup {
        private final Map<IndexCode, BigDecimal> byDate = new EnumMap<>(IndexCode.class);
        private final Map<IndexCode, BigDecimal> latest = new EnumMap<>(IndexCode.class);

        InMemoryLookup atDate(IndexCode code, BigDecimal value) {
            byDate.put(code, value);
            return this;
        }

        InMemoryLookup latest(IndexCode code, BigDecimal value) {
            latest.put(code, value);
            return this;
        }

        @Override
        public Optional<BigDecimal> valueAt(IndexCode indexCode, LocalDate date) {
            return Optional.ofNullable(byDate.get(indexCode));
        }

        @Override
        public Optional<BigDecimal> latestValue(IndexCode indexCode) {
            return Optional.ofNullable(latest.get(indexCode));
        }
    }
}
