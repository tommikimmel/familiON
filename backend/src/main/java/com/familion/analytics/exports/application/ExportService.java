package com.familion.analytics.exports.application;

import com.familion.analytics.application.AnalyticsQuery;
import com.familion.analytics.application.PeriodAggregateRow;
import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.domain.AnalyticsLensCalculator;
import com.familion.analytics.exports.domain.ExportFormat;
import com.familion.analytics.exports.domain.ExportLabels;
import com.familion.analytics.exports.domain.ExportLensResolver;
import com.familion.analytics.exports.domain.InvalidExportRequestException;
import com.familion.common.money.Money;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.View;
import com.familion.common.security.ViewAccess;
import com.familion.common.security.VisibilityPolicy;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class ExportService {

    static final int DEFAULT_PAGE_SIZE = 1000;
    static final int MAX_RANGE_YEARS = 3;
    static final int MIN_SUMMARY_YEAR = 1900;
    static final int MAX_SUMMARY_YEAR = 2200;
    static final String IPC_ACUMULADO_INDEX = "IPC_ACUMULADO";

    private final ExportQuery exportQuery;
    private final AnalyticsQuery analyticsQuery;
    private final VisibilityPolicy visibilityPolicy;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final Clock clock;
    private final Map<ExportFormat, ExportWriter> writersByFormat;
    private final ExportLensResolver lensResolver;
    private final AnalyticsLensCalculator lensCalculator;
    private final int pageSize;

    @Autowired
    public ExportService(
            ExportQuery exportQuery,
            AnalyticsQuery analyticsQuery,
            VisibilityPolicy visibilityPolicy,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            Clock clock,
            List<ExportWriter> writers) {
        this(
                exportQuery,
                analyticsQuery,
                visibilityPolicy,
                familyQueryApi,
                economicsApi,
                clock,
                writers,
                DEFAULT_PAGE_SIZE);
    }

    ExportService(
            ExportQuery exportQuery,
            AnalyticsQuery analyticsQuery,
            VisibilityPolicy visibilityPolicy,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            Clock clock,
            List<ExportWriter> writers,
            int pageSize) {
        this.exportQuery = Objects.requireNonNull(exportQuery, "exportQuery must not be null");
        this.analyticsQuery =
                Objects.requireNonNull(analyticsQuery, "analyticsQuery must not be null");
        this.visibilityPolicy =
                Objects.requireNonNull(visibilityPolicy, "visibilityPolicy must not be null");
        this.familyQueryApi =
                Objects.requireNonNull(familyQueryApi, "familyQueryApi must not be null");
        this.economicsApi = Objects.requireNonNull(economicsApi, "economicsApi must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.writersByFormat = new EnumMap<>(ExportFormat.class);
        for (ExportWriter writer : Objects.requireNonNull(writers, "writers must not be null")) {
            this.writersByFormat.put(writer.format(), writer);
        }
        this.lensResolver = new ExportLensResolver();
        this.lensCalculator = new AnalyticsLensCalculator();
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        this.pageSize = pageSize;
    }

    public PreparedExport prepareTransactionsExport(
            CurrentUser user,
            ExportFormat requestedFormat,
            LocalDate from,
            LocalDate to,
            View requestedView,
            AnalyticsLens requestedLens) {
        Objects.requireNonNull(user, "user must not be null");
        validateDateRange(from, to);

        ExportFormat format = requestedFormat != null ? requestedFormat : ExportFormat.CSV;
        View view = requestedView != null ? requestedView : View.PERSONAL;
        AnalyticsLens lens = requestedLens != null ? requestedLens : AnalyticsLens.NOMINAL;

        ViewAccess access = visibilityPolicy.resolveForListing(user, view);
        UUID viewerUserId = access == ViewAccess.OWN ? user.userId() : null;
        boolean includeAllowanceIn = access == ViewAccess.OWN;

        ExportWriter writer = writerFor(format);
        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        String lensUnit =
                lensResolver.unit(lens, settings.baseCurrency(), settings.referenceCurrency());

        BigDecimal latestIpc = lens == AnalyticsLens.CONSTANT_BASE
                ? economicsApi.latestValue(IPC_ACUMULADO_INDEX).orElse(null)
                : null;

        Map<YearMonth, BigDecimal> incomeByMonth = lens == AnalyticsLens.INCOME_PCT
                ? exportQuery.sumPostedIncomeByMonth(
                        user.familyId(),
                        viewerUserId,
                        includeAllowanceIn,
                        YearMonth.from(from).atDay(1),
                        YearMonth.from(to).atEndOfMonth())
                : Map.of();

        String filename = "familion-movimientos-" + from + "_" + to + "." + format.extension();
        StreamingResponseBody body = outputStream -> writer.writeTransactions(
                outputStream,
                lensUnit,
                sink -> streamTransactionPages(
                        user.familyId(), viewerUserId, from, to, lens, latestIpc, incomeByMonth, sink));

        return new PreparedExport(filename, format.mediaType(), body);
    }

    public PreparedExport prepareMonthlySummaryExport(
            CurrentUser user,
            ExportFormat requestedFormat,
            Integer requestedYear,
            View requestedView,
            AnalyticsLens requestedLens) {
        Objects.requireNonNull(user, "user must not be null");

        ExportFormat format = requestedFormat != null ? requestedFormat : ExportFormat.CSV;
        View view = requestedView != null ? requestedView : View.PERSONAL;
        AnalyticsLens lens = requestedLens != null ? requestedLens : AnalyticsLens.NOMINAL;

        ViewAccess access = visibilityPolicy.resolveForTotals(user, view);
        UUID viewerUserId = access == ViewAccess.OWN ? user.userId() : null;
        boolean includeAllowanceIn = access == ViewAccess.OWN;

        ExportWriter writer = writerFor(format);
        FamilySettings settings = familyQueryApi.getSettings(user.familyId());
        int year = requestedYear != null
                ? requestedYear
                : LocalDate.now(clock.withZone(settings.timezone())).getYear();
        validateYear(year);

        BigDecimal latestIpc = lens == AnalyticsLens.CONSTANT_BASE
                ? economicsApi.latestValue(IPC_ACUMULADO_INDEX).orElse(null)
                : null;

        LocalDate yearStart = LocalDate.of(year, Month.JANUARY, 1);
        LocalDate yearEnd = LocalDate.of(year, Month.DECEMBER, 31);
        List<PeriodAggregateRow> aggregates = analyticsQuery.aggregateByPeriod(
                user.familyId(),
                viewerUserId,
                includeAllowanceIn,
                AnalyticsGranularity.MONTH,
                lens,
                latestIpc,
                yearStart,
                yearEnd);

        List<MonthlySummaryRow> summaryRows = buildMonthlySummaryRows(year, lens, aggregates);
        String filename = "familion-resumen-mensual-" + year + "." + format.extension();
        StreamingResponseBody body = outputStream -> writer.writeMonthlySummary(outputStream, summaryRows);

        return new PreparedExport(filename, format.mediaType(), body);
    }

    private List<MonthlySummaryRow> buildMonthlySummaryRows(
            int year, AnalyticsLens lens, List<PeriodAggregateRow> aggregates) {
        Map<LocalDate, PeriodAggregateRow> byStart = aggregates.stream()
                .collect(Collectors.toMap(PeriodAggregateRow::periodStart, Function.identity()));

        List<MonthlySummaryRow> result = new ArrayList<>(13);
        BigDecimal totalLensIncome = AnalyticsLensCalculator.ZERO;
        BigDecimal totalLensExpense = AnalyticsLensCalculator.ZERO;
        BigDecimal totalNominalIncome = AnalyticsLensCalculator.ZERO;
        BigDecimal totalNominalExpense = AnalyticsLensCalculator.ZERO;

        for (Month month : Month.values()) {
            LocalDate start = LocalDate.of(year, month, 1);
            PeriodAggregateRow row = byStart.getOrDefault(start, PeriodAggregateRow.empty(start));

            totalLensIncome = totalLensIncome.add(row.lensIncome());
            totalLensExpense = totalLensExpense.add(row.lensExpense());
            totalNominalIncome = totalNominalIncome.add(row.nominalIncome());
            totalNominalExpense = totalNominalExpense.add(row.nominalExpense());

            result.add(toSummaryRow(
                    ExportLabels.monthName(month),
                    lens,
                    row.lensIncome(),
                    row.lensExpense(),
                    row.nominalIncome(),
                    row.nominalExpense(),
                    false));
        }

        result.add(toSummaryRow(
                "Total",
                lens,
                totalLensIncome,
                totalLensExpense,
                totalNominalIncome,
                totalNominalExpense,
                true));

        return List.copyOf(result);
    }

    private MonthlySummaryRow toSummaryRow(
            String label,
            AnalyticsLens lens,
            BigDecimal rawLensIncome,
            BigDecimal rawLensExpense,
            BigDecimal rawNominalIncome,
            BigDecimal rawNominalExpense,
            boolean totalRow) {
        if (lens == AnalyticsLens.INCOME_PCT) {
            if (!AnalyticsLensCalculator.isPositive(rawNominalIncome)) {
                return new MonthlySummaryRow(label, null, null, null, null, totalRow);
            }
            BigDecimal income = AnalyticsLensCalculator.ONE_HUNDRED;
            BigDecimal expense =
                    lensCalculator.incomePctAmount(rawNominalExpense, rawNominalIncome);
            BigDecimal balance = Money.scale(income.subtract(expense));
            BigDecimal savingsRatePct = lensCalculator.savingsRatePct(income, balance);
            return new MonthlySummaryRow(label, income, expense, balance, savingsRatePct, totalRow);
        }

        BigDecimal income = Money.scale(rawLensIncome);
        BigDecimal expense = Money.scale(rawLensExpense);
        BigDecimal balance = Money.scale(income.subtract(expense));
        BigDecimal savingsRatePct = lensCalculator.savingsRatePct(income, balance);
        return new MonthlySummaryRow(label, income, expense, balance, savingsRatePct, totalRow);
    }

    private void streamTransactionPages(
            UUID familyId,
            UUID viewerUserId,
            LocalDate from,
            LocalDate to,
            AnalyticsLens lens,
            BigDecimal latestIpc,
            Map<YearMonth, BigDecimal> incomeByMonth,
            ExportWriter.TransactionRowSink sink)
            throws IOException {
        long offset = 0L;
        while (true) {
            List<ExportTransactionRow> page = exportQuery.findTransactionPage(
                    familyId, viewerUserId, from, to, pageSize, offset);
            for (ExportTransactionRow row : page) {
                sink.accept(toTransactionLine(row, lens, latestIpc, incomeByMonth));
            }
            if (page.size() < pageSize) {
                break;
            }
            offset += page.size();
        }
    }

    private ExportTransactionLine toTransactionLine(
            ExportTransactionRow row,
            AnalyticsLens lens,
            BigDecimal latestIpc,
            Map<YearMonth, BigDecimal> incomeByMonth) {
        BigDecimal monthlyIncome = lens == AnalyticsLens.INCOME_PCT
                ? incomeByMonth.get(YearMonth.from(row.transactionDate()))
                : null;
        BigDecimal lensAmount = lensResolver.resolveTransactionAmount(
                lens,
                row.amount(),
                row.snapshotBaseCurrencyAmount(),
                row.snapshotAmountInReference(),
                row.snapshotIpcIndexValue(),
                latestIpc,
                monthlyIncome);

        return new ExportTransactionLine(
                row.transactionDate(),
                ExportLabels.transactionType(row.type()),
                row.description(),
                row.categoryName(),
                row.accountName(),
                row.paidByFullName(),
                row.attributedToFullName(),
                row.currencyCode(),
                row.amount(),
                lensAmount,
                ExportLabels.installment(row.installmentNumber(), row.totalInstallments()),
                ExportLabels.transactionStatus(row.status()));
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null) {
            throw new InvalidExportRequestException("from", "from date is required");
        }
        if (to == null) {
            throw new InvalidExportRequestException("to", "to date is required");
        }
        if (to.isBefore(from)) {
            throw new InvalidExportRequestException("to", "to date must be on or after from date");
        }
        if (to.isAfter(from.plusYears(MAX_RANGE_YEARS))) {
            throw new InvalidExportRequestException(
                    "to", "Date range must not exceed " + MAX_RANGE_YEARS + " years");
        }
    }

    private static void validateYear(int year) {
        if (year < MIN_SUMMARY_YEAR || year > MAX_SUMMARY_YEAR) {
            throw new InvalidExportRequestException(
                    "year",
                    "year must be between " + MIN_SUMMARY_YEAR + " and " + MAX_SUMMARY_YEAR);
        }
    }

    private ExportWriter writerFor(ExportFormat format) {
        ExportWriter writer = writersByFormat.get(format);
        if (writer == null) {
            throw new InvalidExportRequestException("format", "Unsupported export format: " + format);
        }
        return writer;
    }
}
