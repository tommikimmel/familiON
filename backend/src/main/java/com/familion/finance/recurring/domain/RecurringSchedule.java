package com.familion.finance.recurring.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Pure domain rules for recurring expense monthly due dates and status resolution.
 */
public final class RecurringSchedule {

    private RecurringSchedule() {}

    /**
     * Resolves the due date for {@code targetMonth}, clamping {@code dueDay} to the last day of the
     * month (e.g., day 31 in February becomes 28 or 29).
     */
    public static LocalDate dueDateForMonth(int dueDay, YearMonth targetMonth) {
        Objects.requireNonNull(targetMonth, "targetMonth must not be null");
        if (dueDay < 1 || dueDay > 31) {
            throw new IllegalArgumentException("dueDay must be between 1 and 31");
        }
        int clampedDay = Math.min(dueDay, targetMonth.lengthOfMonth());
        return targetMonth.atDay(clampedDay);
    }

    /**
     * Returns {@code true} when {@code targetMonth} falls within the inclusive month range of
     * {@code [startDate, endDate]}.
     */
    public static boolean isApplicable(LocalDate startDate, LocalDate endDate, YearMonth targetMonth) {
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(targetMonth, "targetMonth must not be null");
        YearMonth startMonth = YearMonth.from(startDate);
        if (targetMonth.isBefore(startMonth)) {
            return false;
        }
        if (endDate != null) {
            YearMonth endMonth = YearMonth.from(endDate);
            if (targetMonth.isAfter(endMonth)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves the status of a recurring expense for {@code targetMonth} on {@code today}.
     */
    public static RecurringMonthStatus resolveStatus(
            LocalDate startDate,
            LocalDate endDate,
            int dueDay,
            YearMonth targetMonth,
            boolean paid,
            LocalDate today) {
        Objects.requireNonNull(today, "today must not be null");
        if (!isApplicable(startDate, endDate, targetMonth)) {
            return RecurringMonthStatus.NOT_APPLICABLE;
        }
        if (paid) {
            return RecurringMonthStatus.PAID;
        }
        LocalDate dueDate = dueDateForMonth(dueDay, targetMonth);
        if (today.isAfter(dueDate)) {
            return RecurringMonthStatus.OVERDUE;
        }
        return RecurringMonthStatus.PENDING;
    }
}
