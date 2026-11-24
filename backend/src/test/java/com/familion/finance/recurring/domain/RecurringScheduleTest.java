package com.familion.finance.recurring.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class RecurringScheduleTest {

    @Test
    void clampsDueDay31InNonLeapFebruaryTo28() {
        assertThat(RecurringSchedule.dueDateForMonth(31, YearMonth.of(2026, 2)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void clampsDueDay31InLeapFebruaryTo29() {
        assertThat(RecurringSchedule.dueDateForMonth(31, YearMonth.of(2024, 2)))
                .isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void clampsDueDay31InThirtyDayMonthTo30() {
        assertThat(RecurringSchedule.dueDateForMonth(31, YearMonth.of(2026, 4)))
                .isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void preservesDueDayWithinMonthLength() {
        assertThat(RecurringSchedule.dueDateForMonth(15, YearMonth.of(2026, 11)))
                .isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(RecurringSchedule.dueDateForMonth(31, YearMonth.of(2026, 12)))
                .isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void rejectsOutOfRangeDueDay() {
        assertThatThrownBy(() -> RecurringSchedule.dueDateForMonth(0, YearMonth.of(2026, 11)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecurringSchedule.dueDateForMonth(32, YearMonth.of(2026, 11)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolvesNotApplicableWhenMonthBeforeStartOrAfterEnd() {
        LocalDate start = LocalDate.of(2026, 3, 15);
        LocalDate end = LocalDate.of(2026, 6, 5);
        LocalDate today = LocalDate.of(2026, 5, 10);

        assertThat(RecurringSchedule.resolveStatus(
                        start, end, 10, YearMonth.of(2026, 2), false, today))
                .isEqualTo(RecurringMonthStatus.NOT_APPLICABLE);
        assertThat(RecurringSchedule.resolveStatus(
                        start, end, 10, YearMonth.of(2026, 7), false, today))
                .isEqualTo(RecurringMonthStatus.NOT_APPLICABLE);
        assertThat(RecurringSchedule.resolveStatus(
                        start, end, 10, YearMonth.of(2026, 3), false, today))
                .isEqualTo(RecurringMonthStatus.OVERDUE);
        assertThat(RecurringSchedule.resolveStatus(
                        start, end, 10, YearMonth.of(2026, 6), false, today))
                .isEqualTo(RecurringMonthStatus.PENDING);
    }

    @Test
    void resolvesPaidWhenTransactionExistsInMonth() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate today = LocalDate.of(2026, 11, 20);

        assertThat(RecurringSchedule.resolveStatus(
                        start, null, 10, YearMonth.of(2026, 11), true, today))
                .isEqualTo(RecurringMonthStatus.PAID);
    }

    @Test
    void resolvesPendingOnDueDateAndOverdueTheDayAfter() {
        LocalDate start = LocalDate.of(2026, 1, 1);

        assertThat(RecurringSchedule.resolveStatus(
                        start, null, 15, YearMonth.of(2026, 11), false, LocalDate.of(2026, 11, 14)))
                .isEqualTo(RecurringMonthStatus.PENDING);
        assertThat(RecurringSchedule.resolveStatus(
                        start, null, 15, YearMonth.of(2026, 11), false, LocalDate.of(2026, 11, 15)))
                .isEqualTo(RecurringMonthStatus.PENDING);
        assertThat(RecurringSchedule.resolveStatus(
                        start, null, 15, YearMonth.of(2026, 11), false, LocalDate.of(2026, 11, 16)))
                .isEqualTo(RecurringMonthStatus.OVERDUE);
    }
}
