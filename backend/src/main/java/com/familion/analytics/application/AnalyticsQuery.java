package com.familion.analytics.application;

import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AnalyticsQuery {

    List<PeriodAggregateRow> aggregateByPeriod(
            UUID familyId,
            UUID viewerUserId,
            boolean includeAllowanceIn,
            AnalyticsGranularity granularity,
            AnalyticsLens lens,
            BigDecimal latestIpc,
            LocalDate from,
            LocalDate to);

    BigDecimal sumProjectedInstallments(
            UUID familyId, UUID viewerUserId, LocalDate from, LocalDate to);
}
