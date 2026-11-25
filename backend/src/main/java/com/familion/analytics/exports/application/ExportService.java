package com.familion.analytics.exports.application;

import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.exports.domain.ExportFormat;
import com.familion.analytics.exports.domain.ExportLabels;
import com.familion.analytics.exports.domain.ExportLensResolver;
import com.familion.analytics.exports.domain.InvalidExportRequestException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.View;
import com.familion.common.security.ViewAccess;
import com.familion.common.security.VisibilityPolicy;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class ExportService {

    static final int DEFAULT_PAGE_SIZE = 1000;
    static final int MAX_RANGE_YEARS = 3;
    static final String IPC_ACUMULADO_INDEX = "IPC_ACUMULADO";

    private final ExportQuery exportQuery;
    private final VisibilityPolicy visibilityPolicy;
    private final FamilyQueryApi familyQueryApi;
    private final EconomicsApi economicsApi;
    private final Map<ExportFormat, ExportWriter> writersByFormat;
    private final ExportLensResolver lensResolver;
    private final int pageSize;

    @Autowired
    public ExportService(
            ExportQuery exportQuery,
            VisibilityPolicy visibilityPolicy,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            List<ExportWriter> writers) {
        this(
                exportQuery,
                visibilityPolicy,
                familyQueryApi,
                economicsApi,
                writers,
                DEFAULT_PAGE_SIZE);
    }

    ExportService(
            ExportQuery exportQuery,
            VisibilityPolicy visibilityPolicy,
            FamilyQueryApi familyQueryApi,
            EconomicsApi economicsApi,
            List<ExportWriter> writers,
            int pageSize) {
        this.exportQuery = Objects.requireNonNull(exportQuery, "exportQuery must not be null");
        this.visibilityPolicy =
                Objects.requireNonNull(visibilityPolicy, "visibilityPolicy must not be null");
        this.familyQueryApi =
                Objects.requireNonNull(familyQueryApi, "familyQueryApi must not be null");
        this.economicsApi = Objects.requireNonNull(economicsApi, "economicsApi must not be null");
        this.writersByFormat = new EnumMap<>(ExportFormat.class);
        for (ExportWriter writer : Objects.requireNonNull(writers, "writers must not be null")) {
            this.writersByFormat.put(writer.format(), writer);
        }
        this.lensResolver = new ExportLensResolver();
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

    private ExportWriter writerFor(ExportFormat format) {
        ExportWriter writer = writersByFormat.get(format);
        if (writer == null) {
            throw new InvalidExportRequestException("format", "Unsupported export format: " + format);
        }
        return writer;
    }
}
