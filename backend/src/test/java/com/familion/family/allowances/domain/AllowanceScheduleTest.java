package com.familion.family.allowances.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familion.common.error.BusinessRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AllowanceScheduleTest {

    @Test
    void validateDayOfPeriodAcceptsValidRangesAndRejectsOutOfRange() {
        AllowanceSchedule.validateDayOfPeriod(AllowanceFrequency.WEEKLY, 1);
        AllowanceSchedule.validateDayOfPeriod(AllowanceFrequency.WEEKLY, 7);
        AllowanceSchedule.validateDayOfPeriod(AllowanceFrequency.MONTHLY, 1);
        AllowanceSchedule.validateDayOfPeriod(AllowanceFrequency.MONTHLY, 31);

        assertThatThrownBy(
                        () -> AllowanceSchedule.validateDayOfPeriod(AllowanceFrequency.WEEKLY, 0))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () -> AllowanceSchedule.validateDayOfPeriod(AllowanceFrequency.WEEKLY, 8))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () -> AllowanceSchedule.validateDayOfPeriod(AllowanceFrequency.MONTHLY, 0))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () -> AllowanceSchedule.validateDayOfPeriod(AllowanceFrequency.MONTHLY, 32))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void weeklyNextDisbursementWithoutPreviousDisbursementUsesTodayOrNextOccurrence() {
        // 2026-11-16 is Monday (day 1)
        LocalDate monday = LocalDate.of(2026, 11, 16);
        assertThat(AllowanceSchedule.nextDisbursementDate(AllowanceFrequency.WEEKLY, 1, null, monday))
                .isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(AllowanceSchedule.nextDisbursementDate(AllowanceFrequency.WEEKLY, 5, null, monday))
                .isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(AllowanceSchedule.nextDisbursementDate(AllowanceFrequency.WEEKLY, 7, null, monday))
                .isEqualTo(LocalDate.of(2026, 11, 22));

        // 2026-11-18 is Wednesday (day 3) -> Monday is next week
        LocalDate wednesday = LocalDate.of(2026, 11, 18);
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.WEEKLY, 1, null, wednesday))
                .isEqualTo(LocalDate.of(2026, 11, 23));
    }

    @Test
    void weeklyNextDisbursementWithPreviousDisbursementIsStrictlyAfterLastDisbursement() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        // Same day of week -> next week (+7 days)
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.WEEKLY, 1, monday, monday))
                .isEqualTo(LocalDate.of(2026, 11, 23));
        // Later day in same week -> Friday of that week
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.WEEKLY, 5, monday, monday))
                .isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void monthlyNextDisbursementClampsDay31To30And28AndLeapYear29() {
        // Never disbursed: Nov 2026 has 30 days -> 31 clamps to Nov 30
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.MONTHLY, 31, null, LocalDate.of(2026, 11, 15)))
                .isEqualTo(LocalDate.of(2026, 11, 30));

        // Disbursed Jan 31, 2026 -> Feb 28, 2026 (non-leap year)
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.MONTHLY,
                                31,
                                LocalDate.of(2026, 1, 31),
                                LocalDate.of(2026, 1, 31)))
                .isEqualTo(LocalDate.of(2026, 2, 28));

        // Disbursed Jan 31, 2024 -> Feb 29, 2024 (leap year)
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.MONTHLY,
                                31,
                                LocalDate.of(2024, 1, 31),
                                LocalDate.of(2024, 1, 31)))
                .isEqualTo(LocalDate.of(2024, 2, 29));

        // Disbursed Feb 28, 2026 -> Mar 31, 2026
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.MONTHLY,
                                31,
                                LocalDate.of(2026, 2, 28),
                                LocalDate.of(2026, 2, 28)))
                .isEqualTo(LocalDate.of(2026, 3, 31));

        // Disbursed Mar 31, 2026 -> Apr 30, 2026
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.MONTHLY,
                                31,
                                LocalDate.of(2026, 3, 31),
                                LocalDate.of(2026, 3, 31)))
                .isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void monthlyNextDisbursementRespectsDayBeforeAndAfterTarget() {
        // Never disbursed, today == dayOfPeriod -> today
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.MONTHLY, 15, null, LocalDate.of(2026, 11, 15)))
                .isEqualTo(LocalDate.of(2026, 11, 15));

        // Never disbursed, today > dayOfPeriod -> next month
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.MONTHLY, 10, null, LocalDate.of(2026, 11, 15)))
                .isEqualTo(LocalDate.of(2026, 12, 10));

        // Disbursed before dayOfPeriod in same month -> same month's dayOfPeriod
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.MONTHLY,
                                15,
                                LocalDate.of(2026, 11, 5),
                                LocalDate.of(2026, 11, 15)))
                .isEqualTo(LocalDate.of(2026, 11, 15));

        // Disbursed on dayOfPeriod -> next month's dayOfPeriod
        assertThat(
                        AllowanceSchedule.nextDisbursementDate(
                                AllowanceFrequency.MONTHLY,
                                15,
                                LocalDate.of(2026, 11, 15),
                                LocalDate.of(2026, 11, 15)))
                .isEqualTo(LocalDate.of(2026, 12, 15));
    }
}
