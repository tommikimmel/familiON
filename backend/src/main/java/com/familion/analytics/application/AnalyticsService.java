package com.familion.analytics.application;

import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.domain.AnalyticsLensCalculator;
import com.familion.analytics.domain.AnalyticsPeriod;
import com.familion.analytics.domain.InvalidAnalyticsRequestException;
import com.familion.analytics.domain.PeriodResolver;
import com.familion.common.money.Money;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.View;
import com.familion.common.security.ViewAccess;
import com.familion.common.security.VisibilityPolicy;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    static final String IPC_ACUMULADO_INDEX = "IPC_ACUMULADO";
    static final int MIN_SERIES_PERIODS = 1;
    static final int MAX_SERIES_PERIODS = 24;
    private static final int REAL_VARIATION_HISTORY_PERIODS = 3;

    private final AnalyticsQuery analyticsQuery;
    private final VisibilityPolicy visibilityPolicy;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final Clock clock;
    private final PeriodResolver periodResolver;
    private final AnalyticsLensCalculator lensCalculator;

    public AnalyticsService(
            AnalyticsQuery analyticsQuery,
            VisibilityPolicy visibilityPolicy,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            Clock clock) {
        this.analyticsQuery = analyticsQuery;
        this.visibilityPolicy = visibilityPolicy;
        this.familyQueryApi = familyQueryApi;
        this.economicsApi = economicsApi;
        this.clock = clock;
        this.periodResolver = new PeriodResolver();
        this.lensCalculator = new AnalyticsLensCalculator();
    }

    public AnalyticsSummary getSummary(
            CurrentUser user,
            View requestedView,
            AnalyticsLens requestedLens,
            AnalyticsGranularity requestedGranularity,
            LocalDate requestedDate) {
        Objects.requireNonNull(user, "user must not be null");
        View view = requestedView != null ? requestedView : View.PERSONAL;
        AnalyticsLens lens = requestedLens != null ? requestedLens : AnalyticsLens.NOMINAL;
        AnalyticsGranularity granularity =
                requestedGranularity != null ? requestedGranularity : AnalyticsGranularity.MONTH;

        ViewAccess access = visibilityPolicy.resolveForTotals(user, view);
        UUID viewerUserId = access == ViewAccess.OWN ? user.userId() : null;
        boolean includeAllowanceIn = access == ViewAccess.OWN;

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        LocalDate effectiveDate = resolveDate(requestedDate, settings);
        AnalyticsPeriod period = periodResolver.resolve(granularity, effectiveDate);
        List<AnalyticsPeriod> previousThree =
                periodResolver.previousPeriods(period, REAL_VARIATION_HISTORY_PERIODS);
        AnalyticsPeriod previousPeriod = previousThree.getLast();

        BigDecimal latestIpc = economicsApi.latestValue(IPC_ACUMULADO_INDEX).orElse(null);

        List<PeriodAggregateRow> rows = analyticsQuery.aggregateByPeriod(
                user.familyId(),
                viewerUserId,
                includeAllowanceIn,
                granularity,
                lens,
                latestIpc,
                previousThree.getFirst().from(),
                period.to());

        Map<LocalDate, PeriodAggregateRow> byStart = rows.stream()
                .collect(Collectors.toMap(PeriodAggregateRow::periodStart, Function.identity()));

        PeriodAggregateRow currentRow =
                byStart.getOrDefault(period.from(), PeriodAggregateRow.empty(period.from()));
        PeriodAggregateRow previousRow = byStart.getOrDefault(
                previousPeriod.from(), PeriodAggregateRow.empty(previousPeriod.from()));

        PeriodValues currentValues = toPeriodValues(currentRow, lens);
        PeriodValues previousValues = toPeriodValues(previousRow, lens);

        BigDecimal savingsRatePct =
                lensCalculator.savingsRatePct(currentValues.income(), currentValues.balance());

        PeriodResolver.DateRange nextMonth = periodResolver.nextCalendarMonth(period);
        BigDecimal projectedInstallments = analyticsQuery.sumProjectedInstallments(
                user.familyId(), viewerUserId, nextMonth.from(), nextMonth.to());
        BigDecimal installmentCommitmentPct =
                lensCalculator.percentage(projectedInstallments, currentRow.nominalIncome());

        List<BigDecimal> previousRealExpenses = previousThree.stream()
                .map(p -> byStart.getOrDefault(p.from(), PeriodAggregateRow.empty(p.from()))
                        .constantBaseExpense())
                .toList();
        BigDecimal realVariationPct = lensCalculator.realVariationPct(
                currentRow.constantBaseExpense(), previousRealExpenses);

        boolean lensApproximated = currentRow.lensApproximated() || previousRow.lensApproximated();
        String unit = lensCalculator.unit(
                lens, settings.baseCurrency(), settings.referenceCurrency());

        return new AnalyticsSummary(
                period,
                view,
                lens,
                unit,
                currentValues.income(),
                currentValues.expense(),
                currentValues.balance(),
                savingsRatePct,
                installmentCommitmentPct,
                new AnalyticsSummary.PreviousPeriodTotals(
                        previousValues.income(), previousValues.expense()),
                realVariationPct,
                lensApproximated);
    }

    public AnalyticsSeries getSeries(
            CurrentUser user,
            View requestedView,
            AnalyticsLens requestedLens,
            AnalyticsGranularity requestedGranularity,
            LocalDate requestedDate,
            int periods) {
        Objects.requireNonNull(user, "user must not be null");
        if (periods < MIN_SERIES_PERIODS || periods > MAX_SERIES_PERIODS) {
            throw new InvalidAnalyticsRequestException(
                    "periods",
                    "periods must be between "
                            + MIN_SERIES_PERIODS
                            + " and "
                            + MAX_SERIES_PERIODS);
        }

        View view = requestedView != null ? requestedView : View.PERSONAL;
        AnalyticsLens lens = requestedLens != null ? requestedLens : AnalyticsLens.NOMINAL;
        AnalyticsGranularity granularity =
                requestedGranularity != null ? requestedGranularity : AnalyticsGranularity.MONTH;

        ViewAccess access = visibilityPolicy.resolveForTotals(user, view);
        UUID viewerUserId = access == ViewAccess.OWN ? user.userId() : null;
        boolean includeAllowanceIn = access == ViewAccess.OWN;

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        LocalDate effectiveDate = resolveDate(requestedDate, settings);
        List<AnalyticsPeriod> seriesPeriods =
                periodResolver.seriesPeriods(granularity, effectiveDate, periods);

        BigDecimal latestIpc = lens == AnalyticsLens.CONSTANT_BASE
                ? economicsApi.latestValue(IPC_ACUMULADO_INDEX).orElse(null)
                : null;

        List<PeriodAggregateRow> rows = analyticsQuery.aggregateByPeriod(
                user.familyId(),
                viewerUserId,
                includeAllowanceIn,
                granularity,
                lens,
                latestIpc,
                seriesPeriods.getFirst().from(),
                seriesPeriods.getLast().to());

        Map<LocalDate, PeriodAggregateRow> byStart = rows.stream()
                .collect(Collectors.toMap(PeriodAggregateRow::periodStart, Function.identity()));

        List<AnalyticsSeries.SeriesPoint> points = new ArrayList<>(seriesPeriods.size());
        for (AnalyticsPeriod p : seriesPeriods) {
            PeriodAggregateRow row =
                    byStart.getOrDefault(p.from(), PeriodAggregateRow.empty(p.from()));
            PeriodValues values = toPeriodValues(row, lens);
            points.add(new AnalyticsSeries.SeriesPoint(
                    p.from(), p.label(), values.income(), values.expense(), values.balance()));
        }

        String unit = lensCalculator.unit(
                lens, settings.baseCurrency(), settings.referenceCurrency());

        return new AnalyticsSeries(lens, unit, granularity, List.copyOf(points));
    }

    private LocalDate resolveDate(LocalDate requestedDate, FamilySettings settings) {
        if (requestedDate != null) {
            return requestedDate;
        }
        return LocalDate.now(clock.withZone(settings.timezone()));
    }

    private PeriodValues toPeriodValues(PeriodAggregateRow row, AnalyticsLens lens) {
        if (lens == AnalyticsLens.INCOME_PCT) {
            if (!AnalyticsLensCalculator.isPositive(row.nominalIncome())) {
                return new PeriodValues(null, null, null);
            }
            BigDecimal income = AnalyticsLensCalculator.ONE_HUNDRED;
            BigDecimal expense =
                    lensCalculator.incomePctAmount(row.nominalExpense(), row.nominalIncome());
            BigDecimal balance = Money.scale(income.subtract(expense));
            return new PeriodValues(income, expense, balance);
        }
        BigDecimal income = Money.scale(row.lensIncome());
        BigDecimal expense = Money.scale(row.lensExpense());
        BigDecimal balance = Money.scale(income.subtract(expense));
        return new PeriodValues(income, expense, balance);
    }

    private record PeriodValues(BigDecimal income, BigDecimal expense, BigDecimal balance) {}
}
