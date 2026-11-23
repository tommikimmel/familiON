package com.familion.economics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.economics.domain.IpcChainCalculator.AccumulatedIpc;
import com.familion.economics.domain.IpcChainCalculator.MonthlyIpc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class IpcChainTest {

    private final IpcChainCalculator calculator = new IpcChainCalculator();

    @Test
    void computesAccumulatedSeriesForThreeMonthsWithScale6HalfEven() {
        List<MonthlyIpc> monthly = List.of(
                new MonthlyIpc(LocalDate.of(2026, 1, 1), new BigDecimal("2.5")),
                new MonthlyIpc(LocalDate.of(2026, 2, 1), new BigDecimal("3.0")),
                new MonthlyIpc(LocalDate.of(2026, 3, 1), new BigDecimal("2.0")));

        List<AccumulatedIpc> result = calculator.calculate(monthly);

        assertThat(result)
                .containsExactly(
                        new AccumulatedIpc(LocalDate.of(2026, 1, 1), new BigDecimal("102.500000")),
                        new AccumulatedIpc(LocalDate.of(2026, 2, 1), new BigDecimal("105.575000")),
                        new AccumulatedIpc(LocalDate.of(2026, 3, 1), new BigDecimal("107.686500")));
    }

    @Test
    void sortsUnsortedInputAndNormalizesDatesToFirstDayOfMonth() {
        List<MonthlyIpc> monthly = List.of(
                new MonthlyIpc(LocalDate.of(2026, 3, 31), new BigDecimal("2.0")),
                new MonthlyIpc(LocalDate.of(2026, 1, 31), new BigDecimal("2.5")),
                new MonthlyIpc(LocalDate.of(2026, 2, 28), new BigDecimal("3.0")));

        List<AccumulatedIpc> result = calculator.calculate(monthly);

        assertThat(result)
                .containsExactly(
                        new AccumulatedIpc(LocalDate.of(2026, 1, 1), new BigDecimal("102.500000")),
                        new AccumulatedIpc(LocalDate.of(2026, 2, 1), new BigDecimal("105.575000")),
                        new AccumulatedIpc(LocalDate.of(2026, 3, 1), new BigDecimal("107.686500")));
    }

    @Test
    void appliesHalfEvenRoundingAtScale6() {
        // 100.000000 * 1.011111 = 101.111100
        // 101.111100 * 1.000500 = 101.16165555 -> rounds to 101.161656
        // Also test exact halfway tie to even:
        // 100.000000 * 1.0000005 = 100.000050 (exact at 6 decimals)
        // 100.000000 * 1.000000005 -> 100.0000005 -> HALF_EVEN rounds 0.0000005 to 0.000000 (even)
        List<MonthlyIpc> tieToEven = List.of(
                new MonthlyIpc(LocalDate.of(2026, 1, 1), new BigDecimal("0.0000005")),
                new MonthlyIpc(LocalDate.of(2026, 2, 1), new BigDecimal("0.0000015")));

        List<AccumulatedIpc> result = calculator.calculate(tieToEven);

        assertThat(result.get(0).value()).isEqualByComparingTo("100.000000");
        assertThat(result.get(0).value().scale()).isEqualTo(6);
        assertThat(result.get(1).value()).isEqualByComparingTo("100.000002");
        assertThat(result.get(1).value().scale()).isEqualTo(6);
    }

    @Test
    void returnsEmptyListForEmptyInput() {
        assertThat(calculator.calculate(List.of())).isEmpty();
    }
}
