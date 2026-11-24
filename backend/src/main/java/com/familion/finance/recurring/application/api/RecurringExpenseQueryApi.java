package com.familion.finance.recurring.application.api;

import java.time.LocalDate;
import java.util.List;

public interface RecurringExpenseQueryApi {

    List<RecurringDue> pendingDueBetween(LocalDate from, LocalDate to);
}
