package com.familion.finance.goals.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SavingsGoalCalculatorTest {

    private SavingsGoalCalculator calculator;
    private StubRateProvider rates;

    @BeforeEach
    void setUp() {
        calculator = new SavingsGoalCalculator();
        rates = new StubRateProvider();
    }

    @Test
    void convertsArsContributionToUsdGoalUsingRateSource() {
        LocalDate contribDate = LocalDate.of(2026, 6, 10);
        LocalDate createdDate = LocalDate.of(2026, 1, 1);
        rates.withConversion("ARS", "USD", contribDate, "USD_MEP", new BigDecimal("125.5000"));

        BigDecimal result = calculator.convertContributionToGoalUnit(
                new BigDecimal("150600"),
                "ARS",
                TargetUnit.USD,
                contribDate,
                createdDate,
                "ARS",
                "USD_MEP",
                rates);

        assertThat(result).isEqualByComparingTo("125.5000");
    }

    @Test
    void convertsArsContributionToEurGoal() {
        LocalDate contribDate = LocalDate.of(2026, 6, 10);
        rates.withConversion("ARS", "EUR", contribDate, "USD_MEP", new BigDecimal("80.0000"));

        BigDecimal result = calculator.convertContributionToGoalUnit(
                new BigDecimal("104000"),
                "ARS",
                TargetUnit.EUR,
                contribDate,
                LocalDate.of(2026, 1, 1),
                "ARS",
                "USD_MEP",
                rates);

        assertThat(result).isEqualByComparingTo("80.0000");
    }

    @Test
    void convertsContributionToUvaByConvertingToBaseAndDividingByUvaIndex() {
        LocalDate contribDate = LocalDate.of(2026, 6, 10);
        rates.withConversion("USD", "ARS", contribDate, "USD_MEP", new BigDecimal("120000.0000"));
        rates.withIndex("UVA", contribDate, new BigDecimal("1200.000000"));

        BigDecimal result = calculator.convertContributionToGoalUnit(
                new BigDecimal("100"),
                "USD",
                TargetUnit.UVA,
                contribDate,
                LocalDate.of(2026, 1, 1),
                "ARS",
                "USD_MEP",
                rates);

        assertThat(result).isEqualByComparingTo("100.0000");
    }

    @Test
    void throwsUnprocessableWhenUvaIndexIsMissing() {
        LocalDate contribDate = LocalDate.of(2026, 6, 10);
        rates.withConversion("ARS", "ARS", contribDate, "USD_MEP", new BigDecimal("10000.0000"));

        assertThatThrownBy(() -> calculator.convertContributionToGoalUnit(
                        new BigDecimal("10000"),
                        "ARS",
                        TargetUnit.UVA,
                        contribDate,
                        LocalDate.of(2026, 1, 1),
                        "ARS",
                        "USD_MEP",
                        rates))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("UVA");
    }

    @Test
    void convertsContributionToArsIpcDeflatedToGoalCreationDate() {
        LocalDate createdDate = LocalDate.of(2026, 1, 15);
        LocalDate contribDate = LocalDate.of(2026, 6, 15);
        rates.withConversion("ARS", "ARS", contribDate, "USD_MEP", new BigDecimal("25000.0000"));
        rates.withIndex("IPC_ACUMULADO", createdDate, new BigDecimal("100.000000"));
        rates.withIndex("IPC_ACUMULADO", contribDate, new BigDecimal("125.000000"));

        BigDecimal result = calculator.convertContributionToGoalUnit(
                new BigDecimal("25000"),
                "ARS",
                TargetUnit.ARS_IPC,
                contribDate,
                createdDate,
                "ARS",
                "USD_MEP",
                rates);

        // 25000 * 100 / 125 = 20000.0000
        assertThat(result).isEqualByComparingTo("20000.0000");
    }

    @Test
    void convertsContributionToArsIpcWithoutAdjustmentWhenIpcIsMissing() {
        LocalDate createdDate = LocalDate.of(2026, 1, 15);
        LocalDate contribDate = LocalDate.of(2026, 6, 15);
        rates.withConversion("ARS", "ARS", contribDate, "USD_MEP", new BigDecimal("25000.0000"));

        BigDecimal result = calculator.convertContributionToGoalUnit(
                new BigDecimal("25000"),
                "ARS",
                TargetUnit.ARS_IPC,
                contribDate,
                createdDate,
                "ARS",
                "USD_MEP",
                rates);

        assertThat(result).isEqualByComparingTo("25000.0000");
    }

    @Test
    void rejectsContributionWhenConvertedResultRoundsToZero() {
        LocalDate contribDate = LocalDate.of(2026, 6, 10);
        rates.withConversion("ARS", "USD", contribDate, "USD_MEP", new BigDecimal("0.0000"));

        assertThatThrownBy(() -> calculator.convertContributionToGoalUnit(
                        new BigDecimal("0.01"),
                        "ARS",
                        TargetUnit.USD,
                        contribDate,
                        LocalDate.of(2026, 1, 1),
                        "ARS",
                        "USD_MEP",
                        rates))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void computesProgressAndSuggestedMonthlySavingForFutureTargetDate() {
        SavingsGoalCalculator.Progress progress = calculator.computeProgress(
                new BigDecimal("1000"),
                new BigDecimal("250"),
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 6, 15));

        assertThat(progress.accumulatedInUnit()).isEqualByComparingTo("250.0000");
        assertThat(progress.progressPct()).isEqualByComparingTo("25.0000");
        assertThat(progress.remainingInUnit()).isEqualByComparingTo("750.0000");
        assertThat(progress.monthsLeft()).isEqualTo(5);
        assertThat(progress.suggestedMonthlyInUnit()).isEqualByComparingTo("150.0000");
    }

    @Test
    void clampsMonthsLeftToMinimumOneWhenTargetDateIsNearOrPast() {
        SavingsGoalCalculator.Progress near = calculator.computeProgress(
                new BigDecimal("500"),
                new BigDecimal("200"),
                LocalDate.of(2026, 6, 25),
                LocalDate.of(2026, 6, 15));
        assertThat(near.monthsLeft()).isEqualTo(1);
        assertThat(near.suggestedMonthlyInUnit()).isEqualByComparingTo("300.0000");

        SavingsGoalCalculator.Progress past = calculator.computeProgress(
                new BigDecimal("500"),
                new BigDecimal("200"),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 6, 15));
        assertThat(past.monthsLeft()).isEqualTo(1);
        assertThat(past.suggestedMonthlyInUnit()).isEqualByComparingTo("300.0000");
    }

    @Test
    void returnsNullMonthsLeftAndSuggestedMonthlyWithoutTargetDate() {
        SavingsGoalCalculator.Progress progress = calculator.computeProgress(
                new BigDecimal("1000"),
                new BigDecimal("400"),
                null,
                LocalDate.of(2026, 6, 15));

        assertThat(progress.progressPct()).isEqualByComparingTo("40.0000");
        assertThat(progress.remainingInUnit()).isEqualByComparingTo("600.0000");
        assertThat(progress.monthsLeft()).isNull();
        assertThat(progress.suggestedMonthlyInUnit()).isNull();
    }

    @Test
    void capsProgressAtHundredAndClampsRemainingAtZeroWhenOverFunded() {
        SavingsGoalCalculator.Progress progress = calculator.computeProgress(
                new BigDecimal("1000"),
                new BigDecimal("1250"),
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2026, 6, 15));

        assertThat(progress.accumulatedInUnit()).isEqualByComparingTo("1250.0000");
        assertThat(progress.progressPct()).isEqualByComparingTo("100.0000");
        assertThat(progress.remainingInUnit()).isEqualByComparingTo("0.0000");
        assertThat(progress.monthsLeft()).isEqualTo(6);
        assertThat(progress.suggestedMonthlyInUnit()).isEqualByComparingTo("0.0000");
    }

    @Test
    void computesAccumulatedInBaseTodayForUsdUvaAndArsIpc() {
        LocalDate created = LocalDate.of(2026, 1, 1);
        LocalDate today = LocalDate.of(2026, 6, 15);
        rates.withConversion("USD", "ARS", today, "USD_MEP", new BigDecimal("240000.0000"));
        rates.withIndex("UVA", today, new BigDecimal("1500.000000"));
        rates.withIndex("IPC_ACUMULADO", created, new BigDecimal("100.000000"));
        rates.withIndex("IPC_ACUMULADO", today, new BigDecimal("150.000000"));

        BigDecimal usdInBase = calculator.computeAccumulatedInBaseToday(
                new BigDecimal("200"), TargetUnit.USD, created, today, "ARS", "USD_MEP", rates);
        assertThat(usdInBase).isEqualByComparingTo("240000.0000");

        BigDecimal uvaInBase = calculator.computeAccumulatedInBaseToday(
                new BigDecimal("50"), TargetUnit.UVA, created, today, "ARS", "USD_MEP", rates);
        assertThat(uvaInBase).isEqualByComparingTo("75000.0000");

        BigDecimal arsIpcInBase = calculator.computeAccumulatedInBaseToday(
                new BigDecimal("20000"),
                TargetUnit.ARS_IPC,
                created,
                today,
                "ARS",
                "USD_MEP",
                rates);
        // 20000 * 150 / 100 = 30000.0000
        assertThat(arsIpcInBase).isEqualByComparingTo("30000.0000");
    }

    @Test
    void returnsNullAccumulatedInBaseTodayForUvaWhenIndexIsMissing() {
        BigDecimal result = calculator.computeAccumulatedInBaseToday(
                new BigDecimal("50"),
                TargetUnit.UVA,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 15),
                "ARS",
                "USD_MEP",
                rates);

        assertThat(result).isNull();
    }

    private static final class StubRateProvider implements SavingsGoalCalculator.RateProvider {

        private final Map<String, BigDecimal> conversions = new HashMap<>();
        private final Map<String, BigDecimal> indices = new HashMap<>();
        private final Map<String, BigDecimal> latestIndices = new HashMap<>();

        void withConversion(
                String from, String to, LocalDate date, String source, BigDecimal result) {
            conversions.put(key(from, to, date, source), Money.scale(result));
        }

        void withIndex(String code, LocalDate date, BigDecimal value) {
            indices.put(code + "|" + date, value);
            latestIndices.put(code, value);
        }

        @Override
        public BigDecimal convert(
                BigDecimal amount,
                String fromCurrency,
                String toCurrency,
                LocalDate date,
                String rateSource) {
            BigDecimal stubbed = conversions.get(key(fromCurrency, toCurrency, date, rateSource));
            if (stubbed != null) {
                return stubbed;
            }
            if (fromCurrency.equalsIgnoreCase(toCurrency)) {
                return Money.scale(amount);
            }
            throw new IllegalStateException(
                    "Unexpected conversion: " + fromCurrency + " -> " + toCurrency + " on " + date);
        }

        @Override
        public Optional<BigDecimal> valueAt(String indexCode, LocalDate date) {
            return Optional.ofNullable(indices.get(indexCode + "|" + date));
        }

        @Override
        public Optional<BigDecimal> latestValue(String indexCode) {
            return Optional.ofNullable(latestIndices.get(indexCode));
        }

        private static String key(String from, String to, LocalDate date, String source) {
            return from + "|" + to + "|" + date + "|" + source;
        }
    }
}
