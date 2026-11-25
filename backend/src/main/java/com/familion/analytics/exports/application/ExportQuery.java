package com.familion.analytics.exports.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ExportQuery {

    List<ExportTransactionRow> findTransactionPage(
            UUID familyId,
            UUID viewerUserId,
            LocalDate from,
            LocalDate to,
            int limit,
            long offset);

    Map<YearMonth, BigDecimal> sumPostedIncomeByMonth(
            UUID familyId,
            UUID viewerUserId,
            boolean includeAllowanceIn,
            LocalDate fromMonthStart,
            LocalDate toMonthEnd);
}
