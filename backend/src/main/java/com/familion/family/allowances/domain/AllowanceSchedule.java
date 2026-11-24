package com.familion.family.allowances.domain;

import com.familion.common.error.BusinessRuleException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Pure domain rules for allowance day-of-period validation and next disbursement date calculation.
 */
public final class AllowanceSchedule {

    private AllowanceSchedule() {}

    /**
     * Validates that {@code dayOfPeriod} is within the allowed range for {@code frequency}:
     * 1–7 (Monday = 1) for {@link AllowanceFrequency#WEEKLY}, and 1–31 for
     * {@link AllowanceFrequency#MONTHLY}.
     */
    public static void validateDayOfPeriod(AllowanceFrequency frequency, int dayOfPeriod) {
        Objects.requireNonNull(frequency, "frequency must not be null");
        switch (frequency) {
            case WEEKLY -> {
                if (dayOfPeriod < 1 || dayOfPeriod > 7) {
                    throw new BusinessRuleException(
                            "For WEEKLY allowances, dayOfPeriod must be between 1 (Monday) and 7 (Sunday)");
                }
            }
            case MONTHLY -> {
                if (dayOfPeriod < 1 || dayOfPeriod > 31) {
                    throw new BusinessRuleException(
                            "For MONTHLY allowances, dayOfPeriod must be between 1 and 31");
                }
            }
        }
    }

    /**
     * Computes the next disbursement date: the next occurrence of {@code dayOfPeriod} strictly
     * after {@code lastDisbursementDate} when present, or on/after {@code today} when the allowance
     * has never been disbursed.
     */
    public static LocalDate nextDisbursementDate(
            AllowanceFrequency frequency,
            int dayOfPeriod,
            LocalDate lastDisbursementDate,
            LocalDate today) {
        Objects.requireNonNull(frequency, "frequency must not be null");
        Objects.requireNonNull(today, "today must not be null");
        validateDayOfPeriod(frequency, dayOfPeriod);

        return switch (frequency) {
            case WEEKLY -> nextWeekly(dayOfPeriod, lastDisbursementDate, today);
            case MONTHLY -> nextMonthly(dayOfPeriod, lastDisbursementDate, today);
        };
    }

    private static LocalDate nextWeekly(
            int dayOfPeriod, LocalDate lastDisbursementDate, LocalDate today) {
        DayOfWeek targetDay = DayOfWeek.of(dayOfPeriod);
        if (lastDisbursementDate != null) {
            return lastDisbursementDate.with(TemporalAdjusters.next(targetDay));
        }
        return today.with(TemporalAdjusters.nextOrSame(targetDay));
    }

    private static LocalDate nextMonthly(
            int dayOfPeriod, LocalDate lastDisbursementDate, LocalDate today) {
        if (lastDisbursementDate != null) {
            YearMonth baseMonth = YearMonth.from(lastDisbursementDate);
            LocalDate candidate = clampedDateForMonth(baseMonth, dayOfPeriod);
            if (candidate.isAfter(lastDisbursementDate)) {
                return candidate;
            }
            return clampedDateForMonth(baseMonth.plusMonths(1), dayOfPeriod);
        }

        YearMonth baseMonth = YearMonth.from(today);
        LocalDate candidate = clampedDateForMonth(baseMonth, dayOfPeriod);
        if (!candidate.isBefore(today)) {
            return candidate;
        }
        return clampedDateForMonth(baseMonth.plusMonths(1), dayOfPeriod);
    }

    private static LocalDate clampedDateForMonth(YearMonth month, int dayOfPeriod) {
        int clampedDay = Math.min(dayOfPeriod, month.lengthOfMonth());
        return month.atDay(clampedDay);
    }
}
