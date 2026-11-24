package com.familion.analytics.application;

import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.domain.AnalyticsLensCalculator;
import com.familion.analytics.domain.AnalyticsPeriod;
import com.familion.analytics.domain.CalendarItemKind;
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
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    static final int MAX_CALENDAR_DAYS = 62;
    private static final int REAL_VARIATION_HISTORY_PERIODS = 3;

    private final AnalyticsQuery analyticsQuery;
    private final AnalyticsCache analyticsCache;
    private final VisibilityPolicy visibilityPolicy;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final Clock clock;
    private final PeriodResolver periodResolver;
    private final AnalyticsLensCalculator lensCalculator;

    public AnalyticsService(
            AnalyticsQuery analyticsQuery,
            AnalyticsCache analyticsCache,
            VisibilityPolicy visibilityPolicy,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            Clock clock) {
        this.analyticsQuery = analyticsQuery;
        this.analyticsCache = analyticsCache;
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

        FamilySettings settings = null;
        LocalDate effectiveDate = requestedDate;
        if (effectiveDate == null) {
            settings = familyQueryApi.getSettings(user.familyId());
            effectiveDate = LocalDate.now(clock.withZone(settings.timezone()));
        }

        String cacheHash = AnalyticsCacheKeyBuilder.hash(
                "summary",
                Map.of(
                        "date", effectiveDate.toString(),
                        "granularity", granularity.name(),
                        "lens", lens.name()),
                user.userId(),
                view);
        Optional<AnalyticsSummary> cached =
                analyticsCache.get(user.familyId(), cacheHash, AnalyticsSummary.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        if (settings == null) {
            settings = familyQueryApi.getSettings(user.familyId());
        }

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

        AnalyticsSummary result = new AnalyticsSummary(
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

        analyticsCache.put(user.familyId(), cacheHash, result);
        return result;
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

        FamilySettings settings = null;
        LocalDate effectiveDate = requestedDate;
        if (effectiveDate == null) {
            settings = familyQueryApi.getSettings(user.familyId());
            effectiveDate = LocalDate.now(clock.withZone(settings.timezone()));
        }

        String cacheHash = AnalyticsCacheKeyBuilder.hash(
                "series",
                Map.of(
                        "date", effectiveDate.toString(),
                        "granularity", granularity.name(),
                        "lens", lens.name(),
                        "periods", String.valueOf(periods)),
                user.userId(),
                view);
        Optional<AnalyticsSeries> cached =
                analyticsCache.get(user.familyId(), cacheHash, AnalyticsSeries.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        if (settings == null) {
            settings = familyQueryApi.getSettings(user.familyId());
        }

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

        AnalyticsSeries result = new AnalyticsSeries(lens, unit, granularity, List.copyOf(points));
        analyticsCache.put(user.familyId(), cacheHash, result);
        return result;
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

        FamilySettings settings = null;
        LocalDate effectiveDate = requestedDate;
        if (effectiveDate == null) {
            settings = familyQueryApi.getSettings(user.familyId());
            effectiveDate = LocalDate.now(clock.withZone(settings.timezone()));
        }

        String cacheHash = AnalyticsCacheKeyBuilder.hash(
                "by-category",
                Map.of(
                        "date", effectiveDate.toString(),
                        "granularity", granularity.name(),
                        "lens", lens.name()),
                user.userId(),
                view);
        Optional<CategoryBreakdown> cached =
                analyticsCache.get(user.familyId(), cacheHash, CategoryBreakdown.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        if (settings == null) {
            settings = familyQueryApi.getSettings(user.familyId());
        }

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

        CategoryBreakdown result =
                new CategoryBreakdown(period, lens, unit, total, List.copyOf(slices));
        analyticsCache.put(user.familyId(), cacheHash, result);
        return result;
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

        FamilySettings settings = null;
        LocalDate effectiveDate = requestedDate;
        if (effectiveDate == null) {
            settings = familyQueryApi.getSettings(user.familyId());
            effectiveDate = LocalDate.now(clock.withZone(settings.timezone()));
        }

        String cacheHash = AnalyticsCacheKeyBuilder.hash(
                "by-person",
                Map.of(
                        "date", effectiveDate.toString(),
                        "granularity", granularity.name(),
                        "lens", lens.name()),
                user.userId(),
                View.FAMILY);
        Optional<PersonBreakdown> cached =
                analyticsCache.get(user.familyId(), cacheHash, PersonBreakdown.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        if (settings == null) {
            settings = familyQueryApi.getSettings(user.familyId());
        }

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

        PersonBreakdown result =
                new PersonBreakdown(period, lens, unit, total, List.copyOf(slices));
        analyticsCache.put(user.familyId(), cacheHash, result);
        return result;
    }

    public CalendarView getCalendar(
            CurrentUser user, View requestedView, LocalDate from, LocalDate to) {
        Objects.requireNonNull(user, "user must not be null");
        if (from == null) {
            throw new InvalidAnalyticsRequestException("from", "from date is required");
        }
        if (to == null) {
            throw new InvalidAnalyticsRequestException("to", "to date is required");
        }
        if (to.isBefore(from)) {
            throw new InvalidAnalyticsRequestException("to", "to date must be on or after from date");
        }
        long dayCount = ChronoUnit.DAYS.between(from, to) + 1;
        if (dayCount > MAX_CALENDAR_DAYS) {
            throw new InvalidAnalyticsRequestException(
                    "to", "Date range must not exceed " + MAX_CALENDAR_DAYS + " days");
        }

        View view = requestedView != null ? requestedView : View.PERSONAL;
        ViewAccess access = visibilityPolicy.resolveForListing(user, view);
        UUID viewerUserId = access == ViewAccess.OWN ? user.userId() : null;
        boolean isPersonal = access == ViewAccess.OWN;

        String cacheHash = AnalyticsCacheKeyBuilder.hash(
                "calendar",
                Map.of("from", from.toString(), "to", to.toString()),
                user.userId(),
                view);
        Optional<CalendarView> cached =
                analyticsCache.get(user.familyId(), cacheHash, CalendarView.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        Map<LocalDate, BigDecimal> incomeByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> expenseByDate = new HashMap<>();
        for (CalendarRows.DailyTotalRow totalRow : analyticsQuery.findCalendarDailyTotals(
                user.familyId(), viewerUserId, isPersonal, from, to)) {
            incomeByDate.put(totalRow.date(), totalRow.income());
            expenseByDate.put(totalRow.date(), totalRow.expense());
        }

        Map<LocalDate, List<CalendarView.CalendarItem>> itemsByDate = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            itemsByDate.put(d, new ArrayList<>());
        }

        // 1 & 2. POSTED transactions and PROJECTED installments
        for (CalendarRows.TransactionItemRow tx : analyticsQuery.findCalendarTransactionItems(
                user.familyId(), viewerUserId, from, to)) {
            List<CalendarView.CalendarItem> dayItems = itemsByDate.get(tx.date());
            if (dayItems == null) {
                continue;
            }
            if ("PROJECTED".equals(tx.status()) && tx.installmentPlanId() != null) {
                dayItems.add(new CalendarView.CalendarItem(
                        CalendarItemKind.INSTALLMENT,
                        tx.id(),
                        resolveInstallmentTitle(tx),
                        tx.amount(),
                        tx.currencyCode()));
            } else {
                dayItems.add(new CalendarView.CalendarItem(
                        CalendarItemKind.TRANSACTION,
                        tx.id(),
                        resolveTransactionTitle(tx),
                        tx.amount(),
                        tx.currencyCode()));
            }
        }

        // 3. Unpaid active recurring expenses (clamped to end of month)
        List<CalendarRows.RecurringExpenseRow> recurringRows =
                analyticsQuery.findActiveRecurringExpenses(user.familyId(), viewerUserId, from, to);
        if (!recurringRows.isEmpty()) {
            YearMonth startYm = YearMonth.from(from);
            YearMonth endYm = YearMonth.from(to);
            Set<CalendarRows.PaidRecurringKey> paidKeys = analyticsQuery.findPaidRecurringKeys(
                    user.familyId(), startYm.atDay(1), endYm.atEndOfMonth());

            for (YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
                for (CalendarRows.RecurringExpenseRow rec : recurringRows) {
                    int clampedDay = Math.min(rec.dueDay(), ym.lengthOfMonth());
                    LocalDate dueDate = ym.atDay(clampedDay);
                    if (dueDate.isBefore(from) || dueDate.isAfter(to)) {
                        continue;
                    }
                    if (dueDate.isBefore(rec.startDate())) {
                        continue;
                    }
                    if (rec.endDate() != null && dueDate.isAfter(rec.endDate())) {
                        continue;
                    }
                    if (paidKeys.contains(new CalendarRows.PaidRecurringKey(rec.id(), ym))) {
                        continue;
                    }
                    itemsByDate
                            .get(dueDate)
                            .add(new CalendarView.CalendarItem(
                                    CalendarItemKind.RECURRING_DUE,
                                    rec.id(),
                                    rec.name(),
                                    rec.baseAmount(),
                                    rec.currencyCode()));
                }
            }
        }

        // 4. Vehicle dues (family-wide, only in FAMILY view)
        if (!isPersonal) {
            for (CalendarRows.VehicleDueRow due :
                    analyticsQuery.findVehicleDues(user.familyId(), from, to)) {
                List<CalendarView.CalendarItem> dayItems = itemsByDate.get(due.dueDate());
                if (dayItems != null) {
                    dayItems.add(new CalendarView.CalendarItem(
                            CalendarItemKind.VEHICLE_DUE, due.refId(), due.title(), null, null));
                }
            }
        }

        // 5. Active allowances
        List<CalendarRows.AllowanceRow> allowances =
                analyticsQuery.findActiveAllowances(user.familyId(), viewerUserId);
        if (!allowances.isEmpty()) {
            YearMonth startYm = YearMonth.from(from);
            YearMonth endYm = YearMonth.from(to);
            for (CalendarRows.AllowanceRow allowance : allowances) {
                String title = "Mensualidad · " + allowance.childFullName();
                if ("WEEKLY".equals(allowance.frequency())) {
                    int targetDow = Math.min(Math.max(allowance.dayOfPeriod(), 1), 7);
                    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                        if (d.getDayOfWeek().getValue() == targetDow) {
                            itemsByDate
                                    .get(d)
                                    .add(new CalendarView.CalendarItem(
                                            CalendarItemKind.ALLOWANCE,
                                            allowance.id(),
                                            title,
                                            allowance.amount(),
                                            allowance.currencyCode()));
                        }
                    }
                } else {
                    for (YearMonth ym = startYm; !ym.isAfter(endYm); ym = ym.plusMonths(1)) {
                        int clampedDay = Math.min(allowance.dayOfPeriod(), ym.lengthOfMonth());
                        LocalDate dueDate = ym.atDay(clampedDay);
                        if (!dueDate.isBefore(from) && !dueDate.isAfter(to)) {
                            itemsByDate
                                    .get(dueDate)
                                    .add(new CalendarView.CalendarItem(
                                            CalendarItemKind.ALLOWANCE,
                                            allowance.id(),
                                            title,
                                            allowance.amount(),
                                            allowance.currencyCode()));
                        }
                    }
                }
            }
        }

        List<CalendarView.CalendarDay> days = new ArrayList<>((int) dayCount);
        for (Map.Entry<LocalDate, List<CalendarView.CalendarItem>> entry : itemsByDate.entrySet()) {
            LocalDate date = entry.getKey();
            days.add(new CalendarView.CalendarDay(
                    date,
                    incomeByDate.getOrDefault(date, AnalyticsLensCalculator.ZERO),
                    expenseByDate.getOrDefault(date, AnalyticsLensCalculator.ZERO),
                    List.copyOf(entry.getValue())));
        }

        CalendarView result = new CalendarView(from, to, List.copyOf(days));
        analyticsCache.put(user.familyId(), cacheHash, result);
        return result;
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

    private static String resolveTransactionTitle(CalendarRows.TransactionItemRow tx) {
        if (tx.description() != null && !tx.description().isBlank()) {
            return tx.description();
        }
        if (tx.categoryName() != null && !tx.categoryName().isBlank()) {
            return tx.categoryName();
        }
        return "Movimiento";
    }

    private static String resolveInstallmentTitle(CalendarRows.TransactionItemRow tx) {
        if (tx.description() != null && !tx.description().isBlank()) {
            return tx.description();
        }
        if (tx.planDescription() != null && !tx.planDescription().isBlank()) {
            if (tx.installmentNumber() != null && tx.totalInstallments() != null) {
                return tx.planDescription()
                        + " ("
                        + tx.installmentNumber()
                        + "/"
                        + tx.totalInstallments()
                        + ")";
            }
            return tx.planDescription();
        }
        return "Cuota";
    }

    private record PeriodValues(BigDecimal income, BigDecimal expense, BigDecimal balance) {}
}
