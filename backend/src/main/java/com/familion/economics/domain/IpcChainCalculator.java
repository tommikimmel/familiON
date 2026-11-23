package com.familion.economics.domain;

import com.familion.common.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Computes the cumulative IPC index series ({@link IndexCode#IPC_ACUMULADO}) from monthly
 * percentage variations ({@link IndexCode#IPC_MENSUAL}).
 *
 * <p>The month immediately preceding the first available monthly IPC entry has an implicit base
 * value of {@code 100.000000}. Each subsequent month is computed as {@code acum[m] = acum[m-1] * (1
 * + ipc[m] / 100)} rounded to scale 6 using {@link java.math.RoundingMode#HALF_EVEN}, with
 * {@code effectiveDate} normalized to the first day of the month.
 */
public final class IpcChainCalculator {

    public static final BigDecimal INITIAL_BASE = new BigDecimal("100.000000");

    public record MonthlyIpc(LocalDate effectiveDate, BigDecimal monthlyPercent) {
        public MonthlyIpc {
            Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
            Objects.requireNonNull(monthlyPercent, "monthlyPercent must not be null");
        }
    }

    public record AccumulatedIpc(LocalDate effectiveDate, BigDecimal value) {
        public AccumulatedIpc {
            Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }
    }

    public List<AccumulatedIpc> calculate(List<MonthlyIpc> monthlySeries) {
        Objects.requireNonNull(monthlySeries, "monthlySeries must not be null");
        if (monthlySeries.isEmpty()) {
            return List.of();
        }

        List<MonthlyIpc> sorted = monthlySeries.stream()
                .map(entry -> new MonthlyIpc(
                        entry.effectiveDate().withDayOfMonth(1), entry.monthlyPercent()))
                .sorted(Comparator.comparing(MonthlyIpc::effectiveDate))
                .toList();

        // Deduplicate by normalized month, keeping the last entry if duplicates exist.
        Map<LocalDate, BigDecimal> byMonth = new LinkedHashMap<>();
        for (MonthlyIpc entry : sorted) {
            byMonth.put(entry.effectiveDate(), entry.monthlyPercent());
        }

        List<AccumulatedIpc> result = new ArrayList<>(byMonth.size());
        BigDecimal accumulated = INITIAL_BASE;
        for (Map.Entry<LocalDate, BigDecimal> entry : byMonth.entrySet()) {
            BigDecimal factor = BigDecimal.ONE.add(entry.getValue().movePointLeft(2));
            accumulated = Money.rate(accumulated.multiply(factor));
            result.add(new AccumulatedIpc(entry.getKey(), accumulated));
        }
        return List.copyOf(result);
    }
}
