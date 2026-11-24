package com.familion.analytics.application;

import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.domain.AnalyticsLensCalculator;
import com.familion.analytics.domain.AnalyticsPeriod;
import com.familion.analytics.domain.InvalidAnalyticsRequestException;
import com.familion.analytics.domain.PeriodResolver;
import com.familion.common.error.ForbiddenException;
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
    static final String UNCATEGORIZED_LABEL = "Sin categoría";
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

    public CategoryBreakdown getByCategory(
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
        AnalyticsPeriod previousPeriod = periodResolver.previous(period);

        BigDecimal latestIpc = economicsApi.latestValue(IPC_ACUMULADO_INDEX).orElse(null);

        List<CategoryAggregateRow> rows = analyticsQuery.aggregateByCategory(
                user.familyId(), viewerUserId, lens, latestIpc, period.from(), period.to());
        Map<UUID, BigDecimal> previousConstantBase = analyticsQuery.aggregateCategoryConstantBase(
                user.familyId(),
                viewerUserId,
                latestIpc,
                previousPeriod.from(),
                previousPeriod.to());

        BigDecimal nominalIncome = lens == AnalyticsLens.INCOME_PCT
                ? analyticsQuery.sumNominalIncome(
                        user.familyId(),
                        viewerUserId,
                        includeAllowanceIn,
                        period.from(),
                        period.to())
                : null;

        BigDecimal baseTotal = rows.stream()
                .map(r -> lens == AnalyticsLens.INCOME_PCT ? r.nominalAmount() : r.lensAmount())
                .reduce(AnalyticsLensCalculator.ZERO, BigDecimal::add);
        baseTotal = Money.scale(baseTotal);

        BigDecimal total = lens == AnalyticsLens.INCOME_PCT
                ? lensCalculator.incomePctAmount(baseTotal, nominalIncome)
                : baseTotal;

        List<CategoryBreakdown.CategorySlice> slices = new ArrayList<>(rows.size());
        for (CategoryAggregateRow row : rows) {
            BigDecimal sliceBase =
                    lens == AnalyticsLens.INCOME_PCT ? row.nominalAmount() : row.lensAmount();
            BigDecimal amount = lens == AnalyticsLens.INCOME_PCT
                    ? lensCalculator.incomePctAmount(row.nominalAmount(), nominalIncome)
                    : Money.scale(row.lensAmount());
            BigDecimal pct = lensCalculator.percentage(sliceBase, baseTotal);
            if (pct == null) {
                pct = AnalyticsLensCalculator.ZERO;
            }
            BigDecimal prevConst = previousConstantBase.get(row.categoryId());
            BigDecimal variationPct =
                    lensCalculator.variationPct(row.constantBaseAmount(), prevConst);

            String name = row.categoryId() == null ? UNCATEGORIZED_LABEL : row.name();
            slices.add(new CategoryBreakdown.CategorySlice(
                    row.categoryId(),
                    name,
                    row.colorHex(),
                    row.icon(),
                    amount,
                    pct,
                    variationPct));
        }

        String unit = lensCalculator.unit(
                lens, settings.baseCurrency(), settings.referenceCurrency());

        return new CategoryBreakdown(period, lens, unit, total, List.copyOf(slices));
    }

    public PersonBreakdown getByPerson(
            CurrentUser user,
            View requestedView,
            AnalyticsLens requestedLens,
            AnalyticsGranularity requestedGranularity,
            LocalDate requestedDate) {
        Objects.requireNonNull(user, "user must not be null");
        View view = requestedView != null ? requestedView : View.FAMILY;
        if (view != View.FAMILY) {
            throw new InvalidAnalyticsRequestException(
                    "view", "by-person breakdown is only available for FAMILY view");
        }
        AnalyticsLens lens = requestedLens != null ? requestedLens : AnalyticsLens.NOMINAL;
        AnalyticsGranularity granularity =
                requestedGranularity != null ? requestedGranularity : AnalyticsGranularity.MONTH;

        ViewAccess access = visibilityPolicy.resolveForTotals(user, View.FAMILY);
        if (access != ViewAccess.FAMILY_FULL) {
            throw new ForbiddenException("User is not allowed to view person breakdown");
        }

        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        LocalDate effectiveDate = resolveDate(requestedDate, settings);
        AnalyticsPeriod period = periodResolver.resolve(granularity, effectiveDate);

        BigDecimal latestIpc = lens == AnalyticsLens.CONSTANT_BASE
                ? economicsApi.latestValue(IPC_ACUMULADO_INDEX).orElse(null)
                : null;

        List<PersonAggregateRow> rows = analyticsQuery.aggregateByPerson(
                user.familyId(), lens, latestIpc, period.from(), period.to());

        BigDecimal nominalIncome = lens == AnalyticsLens.INCOME_PCT
                ? analyticsQuery.sumNominalIncome(
                        user.familyId(), null, false, period.from(), period.to())
                : null;

        BigDecimal baseTotal = rows.stream()
                .map(r -> lens == AnalyticsLens.INCOME_PCT ? r.nominalAmount() : r.lensAmount())
                .reduce(AnalyticsLensCalculator.ZERO, BigDecimal::add);
        baseTotal = Money.scale(baseTotal);

        BigDecimal total = lens == AnalyticsLens.INCOME_PCT
                ? lensCalculator.incomePctAmount(baseTotal, nominalIncome)
                : baseTotal;

        List<PersonBreakdown.PersonSlice> slices = new ArrayList<>(rows.size());
        for (PersonAggregateRow row : rows) {
            BigDecimal sliceBase =
                    lens == AnalyticsLens.INCOME_PCT ? row.nominalAmount() : row.lensAmount();
            BigDecimal amount = lens == AnalyticsLens.INCOME_PCT
                    ? lensCalculator.incomePctAmount(row.nominalAmount(), nominalIncome)
                    : Money.scale(row.lensAmount());
            BigDecimal pct = lensCalculator.percentage(sliceBase, baseTotal);
            if (pct == null) {
                pct = AnalyticsLensCalculator.ZERO;
            }
            slices.add(new PersonBreakdown.PersonSlice(
                    row.userId(), row.fullName(), row.accentColor(), amount, pct));
        }

        String unit = lensCalculator.unit(
                lens, settings.baseCurrency(), settings.referenceCurrency());

        return new PersonBreakdown(period, lens, unit, total, List.copyOf(slices));
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
