package com.familion.analytics.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PeriodResolver {

    private static final String[] SHORT_MONTHS_ES = {
        "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"
    };

    private static final String[] FULL_MONTHS_ES = {
        "Enero",
        "Febrero",
        "Marzo",
        "Abril",
        "Mayo",
        "Junio",
        "Julio",
        "Agosto",
        "Septiembre",
        "Octubre",
        "Noviembre",
        "Diciembre"
    };

    public AnalyticsPeriod resolve(AnalyticsGranularity granularity, LocalDate referenceDate) {
        Objects.requireNonNull(granularity, "granularity must not be null");
        Objects.requireNonNull(referenceDate, "referenceDate must not be null");

        return switch (granularity) {
            case DAY -> new AnalyticsPeriod(
                    AnalyticsGranularity.DAY,
                    referenceDate,
                    referenceDate,
                    formatDayLabel(referenceDate));
            case WEEK -> {
                LocalDate monday =
                        referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate sunday = monday.plusDays(6);
                yield new AnalyticsPeriod(
                        AnalyticsGranularity.WEEK, monday, sunday, formatWeekLabel(monday));
            }
            case MONTH -> {
                YearMonth ym = YearMonth.from(referenceDate);
                yield new AnalyticsPeriod(
                        AnalyticsGranularity.MONTH,
                        ym.atDay(1),
                        ym.atEndOfMonth(),
                        formatMonthLabel(ym));
            }
            case YEAR -> {
                int year = referenceDate.getYear();
                yield new AnalyticsPeriod(
                        AnalyticsGranularity.YEAR,
                        LocalDate.of(year, Month.JANUARY, 1),
                        LocalDate.of(year, Month.DECEMBER, 31),
                        String.valueOf(year));
            }
        };
    }

    public AnalyticsPeriod previous(AnalyticsPeriod period) {
        Objects.requireNonNull(period, "period must not be null");
        LocalDate prevAnchor = switch (period.granularity()) {
            case DAY -> period.from().minusDays(1);
            case WEEK -> period.from().minusWeeks(1);
            case MONTH -> period.from().minusMonths(1);
            case YEAR -> period.from().minusYears(1);
        };
        return resolve(period.granularity(), prevAnchor);
    }

    public List<AnalyticsPeriod> previousPeriods(AnalyticsPeriod period, int count) {
        Objects.requireNonNull(period, "period must not be null");
        if (count <= 0) {
            return List.of();
        }
        List<AnalyticsPeriod> result = new ArrayList<>(count);
        AnalyticsPeriod cursor = period;
        for (int i = 0; i < count; i++) {
            cursor = previous(cursor);
            result.add(cursor);
        }
        Collections.reverse(result);
        return List.copyOf(result);
    }

    public List<AnalyticsPeriod> seriesPeriods(
            AnalyticsGranularity granularity, LocalDate referenceDate, int periods) {
        Objects.requireNonNull(granularity, "granularity must not be null");
        Objects.requireNonNull(referenceDate, "referenceDate must not be null");
        if (periods < 1) {
            throw new IllegalArgumentException("periods must be at least 1");
        }
        AnalyticsPeriod latest = resolve(granularity, referenceDate);
        List<AnalyticsPeriod> result = new ArrayList<>(periods);
        AnalyticsPeriod cursor = latest;
        for (int i = 0; i < periods; i++) {
            result.add(cursor);
            if (i + 1 < periods) {
                cursor = previous(cursor);
            }
        }
        Collections.reverse(result);
        return List.copyOf(result);
    }

    public DateRange nextCalendarMonth(AnalyticsPeriod period) {
        Objects.requireNonNull(period, "period must not be null");
        YearMonth nextMonth = YearMonth.from(period.to()).plusMonths(1);
        return new DateRange(nextMonth.atDay(1), nextMonth.atEndOfMonth());
    }

    private static String formatDayLabel(LocalDate date) {
        return date.getDayOfMonth()
                + " "
                + SHORT_MONTHS_ES[date.getMonthValue() - 1]
                + " "
                + date.getYear();
    }

    private static String formatWeekLabel(LocalDate date) {
        int isoWeek = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int isoWeekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        return "Semana " + isoWeek + " · " + isoWeekYear;
    }

    private static String formatMonthLabel(YearMonth ym) {
        return FULL_MONTHS_ES[ym.getMonthValue() - 1] + " " + ym.getYear();
    }

    public record DateRange(LocalDate from, LocalDate to) {}
}
