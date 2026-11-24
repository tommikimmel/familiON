package com.familion.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeriodResolverTest {

    private final PeriodResolver resolver = new PeriodResolver();

    @Test
    void resolvesDayPeriodWithSpanishLabel() {
        AnalyticsPeriod period = resolver.resolve(AnalyticsGranularity.DAY, LocalDate.of(2026, 11, 23));

        assertThat(period.granularity()).isEqualTo(AnalyticsGranularity.DAY);
        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(period.label()).isEqualTo("23 nov 2026");
    }

    @Test
    void resolvesSingleDigitDayWithoutLeadingZero() {
        AnalyticsPeriod period = resolver.resolve(AnalyticsGranularity.DAY, LocalDate.of(2026, 1, 5));

        assertThat(period.label()).isEqualTo("5 ene 2026");
    }

    @Test
    void resolvesIsoWeekMondayToSundayWithSpanishLabel() {
        // 2026-11-18 is Wednesday of ISO week 47 (Mon Nov 16 - Sun Nov 22)
        AnalyticsPeriod period = resolver.resolve(AnalyticsGranularity.WEEK, LocalDate.of(2026, 11, 18));

        assertThat(period.granularity()).isEqualTo(AnalyticsGranularity.WEEK);
        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(period.label()).isEqualTo("Semana 47 · 2026");
    }

    @Test
    void resolvesIsoWeekAcrossYearBoundaryUsingWeekBasedYear() {
        // 2025-12-29 is Monday of ISO week 1 of 2026
        AnalyticsPeriod period = resolver.resolve(AnalyticsGranularity.WEEK, LocalDate.of(2025, 12, 31));

        assertThat(period.from()).isEqualTo(LocalDate.of(2025, 12, 29));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(period.label()).isEqualTo("Semana 1 · 2026");
    }

    @Test
    void resolvesMonthPeriodIncludingLeapYear() {
        AnalyticsPeriod nov = resolver.resolve(AnalyticsGranularity.MONTH, LocalDate.of(2026, 11, 23));
        assertThat(nov.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(nov.to()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(nov.label()).isEqualTo("Noviembre 2026");

        AnalyticsPeriod leapFeb = resolver.resolve(AnalyticsGranularity.MONTH, LocalDate.of(2024, 2, 10));
        assertThat(leapFeb.from()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(leapFeb.to()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(leapFeb.label()).isEqualTo("Febrero 2024");
    }

    @Test
    void resolvesYearPeriod() {
        AnalyticsPeriod period = resolver.resolve(AnalyticsGranularity.YEAR, LocalDate.of(2026, 7, 15));

        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(period.to()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(period.label()).isEqualTo("2026");
    }

    @Test
    void computesPreviousPeriodForAllGranularities() {
        assertThat(resolver.previous(resolver.resolve(AnalyticsGranularity.DAY, LocalDate.of(2026, 3, 1))))
                .isEqualTo(resolver.resolve(AnalyticsGranularity.DAY, LocalDate.of(2026, 2, 28)));
        assertThat(resolver.previous(resolver.resolve(AnalyticsGranularity.WEEK, LocalDate.of(2026, 11, 23))))
                .isEqualTo(resolver.resolve(AnalyticsGranularity.WEEK, LocalDate.of(2026, 11, 16)));
        assertThat(resolver.previous(resolver.resolve(AnalyticsGranularity.MONTH, LocalDate.of(2026, 1, 15))))
                .isEqualTo(resolver.resolve(AnalyticsGranularity.MONTH, LocalDate.of(2025, 12, 1)));
        assertThat(resolver.previous(resolver.resolve(AnalyticsGranularity.YEAR, LocalDate.of(2026, 6, 1))))
                .isEqualTo(resolver.resolve(AnalyticsGranularity.YEAR, LocalDate.of(2025, 1, 1)));
    }

    @Test
    void buildsPreviousPeriodsInChronologicalOrder() {
        AnalyticsPeriod nov = resolver.resolve(AnalyticsGranularity.MONTH, LocalDate.of(2026, 11, 15));

        List<AnalyticsPeriod> prev3 = resolver.previousPeriods(nov, 3);

        assertThat(prev3)
                .extracting(AnalyticsPeriod::from)
                .containsExactly(
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 1));
    }

    @Test
    void buildsSeriesPeriodsEndingWithReferenceDateInChronologicalOrder() {
        List<AnalyticsPeriod> series =
                resolver.seriesPeriods(AnalyticsGranularity.MONTH, LocalDate.of(2026, 11, 23), 12);

        assertThat(series).hasSize(12);
        assertThat(series.getFirst().from()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(series.getFirst().label()).isEqualTo("Diciembre 2025");
        assertThat(series.getLast().from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(series.getLast().label()).isEqualTo("Noviembre 2026");
    }

    @Test
    void rejectsNonPositiveSeriesPeriods() {
        assertThatThrownBy(() -> resolver.seriesPeriods(AnalyticsGranularity.MONTH, LocalDate.of(2026, 11, 1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void computesNextCalendarMonthAfterPeriodEnd() {
        AnalyticsPeriod nov = resolver.resolve(AnalyticsGranularity.MONTH, LocalDate.of(2026, 11, 15));
        PeriodResolver.DateRange next = resolver.nextCalendarMonth(nov);
        assertThat(next.from()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(next.to()).isEqualTo(LocalDate.of(2026, 12, 31));

        AnalyticsPeriod dec = resolver.resolve(AnalyticsGranularity.MONTH, LocalDate.of(2026, 12, 1));
        PeriodResolver.DateRange jan = resolver.nextCalendarMonth(dec);
        assertThat(jan.from()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(jan.to()).isEqualTo(LocalDate.of(2027, 1, 31));
    }
}
