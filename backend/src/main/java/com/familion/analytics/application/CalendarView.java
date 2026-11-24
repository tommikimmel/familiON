package com.familion.analytics.application;

import com.familion.analytics.domain.CalendarItemKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CalendarView(LocalDate from, LocalDate to, List<CalendarDay> days) {

    public record CalendarDay(
            LocalDate date, BigDecimal income, BigDecimal expense, List<CalendarItem> items) {}

    public record CalendarItem(
            CalendarItemKind kind,
            UUID refId,
            String title,
            BigDecimal amount,
            String currencyCode) {}
}
