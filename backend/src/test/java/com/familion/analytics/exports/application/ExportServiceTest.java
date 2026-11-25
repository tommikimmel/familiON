package com.familion.analytics.exports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.familion.analytics.application.AnalyticsQuery;
import com.familion.analytics.application.PeriodAggregateRow;
import com.familion.analytics.domain.AnalyticsGranularity;
import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.exports.domain.ExportFormat;
import com.familion.analytics.exports.domain.InvalidExportRequestException;
import com.familion.analytics.exports.infrastructure.format.CsvExportWriter;
import com.familion.analytics.exports.infrastructure.format.XlsxExportWriter;
import com.familion.common.error.ForbiddenException;
import com.familion.common.security.CurrentUser;
import com.familion.common.security.DefaultVisibilityPolicy;
import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.security.View;
import com.familion.economics.application.api.EconomicsApi;
import com.familion.family.application.api.FamilyQueryApi;
import com.familion.family.application.api.FamilySettings;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExportServiceTest {

    private ExportQuery exportQuery;
    private AnalyticsQuery analyticsQuery;
    private FamilyQueryApi familyQueryApi;
    private EconomicsApi economicsApi;
    private ExportService exportService;

    private UUID familyId;
    private CurrentUser adult;
    private CurrentUser childSummary;
    private CurrentUser childOwnOnly;

    @BeforeEach
    void setUp() {
        exportQuery = mock(ExportQuery.class);
        analyticsQuery = mock(AnalyticsQuery.class);
        familyQueryApi = mock(FamilyQueryApi.class);
        economicsApi = mock(EconomicsApi.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneId.of("UTC"));

        exportService = new ExportService(
                exportQuery,
                analyticsQuery,
                new DefaultVisibilityPolicy(),
                familyQueryApi,
                economicsApi,
                fixedClock,
                List.of(new CsvExportWriter(), new XlsxExportWriter()),
                2);

        familyId = UUID.randomUUID();
        adult = new CurrentUser(UUID.randomUUID(), familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childSummary = new CurrentUser(
                UUID.randomUUID(), familyId, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        childOwnOnly =
                new CurrentUser(UUID.randomUUID(), familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        when(familyQueryApi.getSettings(familyId))
                .thenReturn(new FamilySettings(
                        familyId,
                        "Familia Pérez",
                        "ARS",
                        "USD",
                        "USD_MEP",
                        ZoneId.of("America/Argentina/Buenos_Aires"),
                        List.of("ARS", "USD")));
    }

    @Test
    void rejectsRangeExceedingThreeYears() {
        LocalDate from = LocalDate.of(2023, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 2);

        assertThatThrownBy(() -> exportService.prepareTransactionsExport(
                        adult, ExportFormat.CSV, from, to, View.PERSONAL, AnalyticsLens.NOMINAL))
                .isInstanceOf(InvalidExportRequestException.class)
                .hasMessageContaining("3 years");

        verifyNoInteractions(exportQuery);
    }

    @Test
    void rejectsToBeforeFrom() {
        assertThatThrownBy(() -> exportService.prepareTransactionsExport(
                        adult,
                        ExportFormat.CSV,
                        LocalDate.of(2026, 11, 10),
                        LocalDate.of(2026, 11, 1),
                        View.PERSONAL,
                        AnalyticsLens.NOMINAL))
                .isInstanceOf(InvalidExportRequestException.class);
    }

    @Test
    void forbidsChildWithOwnOnlyFromExportingFamilyTransactions() {
        assertThatThrownBy(() -> exportService.prepareTransactionsExport(
                        childOwnOnly,
                        ExportFormat.CSV,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 11, 30),
                        View.FAMILY,
                        AnalyticsLens.NOMINAL))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(exportQuery);
    }

    @Test
    void forbidsChildWithFamilySummaryFromExportingFamilyTransactions() {
        assertThatThrownBy(() -> exportService.prepareTransactionsExport(
                        childSummary,
                        ExportFormat.CSV,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 11, 30),
                        View.FAMILY,
                        AnalyticsLens.NOMINAL))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void streamsTransactionsInPagesUntilPartialPage() throws IOException {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);

        when(exportQuery.findTransactionPage(
                        eq(familyId), eq(adult.userId()), eq(from), eq(to), eq(2), eq(0L)))
                .thenReturn(List.of(sampleRow("Tx 1"), sampleRow("Tx 2")));
        when(exportQuery.findTransactionPage(
                        eq(familyId), eq(adult.userId()), eq(from), eq(to), eq(2), eq(2L)))
                .thenReturn(List.of(sampleRow("Tx 3")));

        PreparedExport prepared = exportService.prepareTransactionsExport(
                adult, ExportFormat.CSV, from, to, View.PERSONAL, AnalyticsLens.NOMINAL);

        assertThat(prepared.filename()).isEqualTo("familion-movimientos-2026-01-01_2026-11-30.csv");
        assertThat(prepared.mediaType().toString()).isEqualTo("text/csv;charset=UTF-8");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        prepared.body().writeTo(out);

        String csv = new String(out.toByteArray(), 3, out.size() - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(4); // header + 3 rows
        assertThat(lines[1]).contains("Tx 1");
        assertThat(lines[2]).contains("Tx 2");
        assertThat(lines[3]).contains("Tx 3");

        verify(exportQuery).findTransactionPage(familyId, adult.userId(), from, to, 2, 0L);
        verify(exportQuery).findTransactionPage(familyId, adult.userId(), from, to, 2, 2L);
    }

    @Test
    void allowsExactThreeYearRange() {
        LocalDate from = LocalDate.of(2023, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 1);
        when(exportQuery.findTransactionPage(any(), any(), any(), any(), anyInt(), anyLong()))
                .thenReturn(List.of());

        PreparedExport prepared = exportService.prepareTransactionsExport(
                adult, ExportFormat.CSV, from, to, View.FAMILY, AnalyticsLens.NOMINAL);
        assertThat(prepared.filename()).isEqualTo("familion-movimientos-2023-01-01_2026-01-01.csv");
    }

    @Test
    void monthlySummaryAllowsChildWithFamilySummaryAndForbidsOwnOnly() throws IOException {
        assertThatThrownBy(() -> exportService.prepareMonthlySummaryExport(
                        childOwnOnly, ExportFormat.CSV, 2026, View.FAMILY, AnalyticsLens.NOMINAL))
                .isInstanceOf(ForbiddenException.class);

        when(analyticsQuery.aggregateByPeriod(
                        eq(familyId),
                        isNull(),
                        eq(false),
                        eq(AnalyticsGranularity.MONTH),
                        eq(AnalyticsLens.NOMINAL),
                        isNull(),
                        eq(LocalDate.of(2026, 1, 1)),
                        eq(LocalDate.of(2026, 12, 31))))
                .thenReturn(List.of(new PeriodAggregateRow(
                        LocalDate.of(2026, 1, 1),
                        new BigDecimal("100000.0000"),
                        new BigDecimal("40000.0000"),
                        new BigDecimal("100000.0000"),
                        new BigDecimal("40000.0000"),
                        new BigDecimal("40000.0000"),
                        false)));

        PreparedExport prepared = exportService.prepareMonthlySummaryExport(
                childSummary, ExportFormat.CSV, 2026, View.FAMILY, AnalyticsLens.NOMINAL);

        assertThat(prepared.filename()).isEqualTo("familion-resumen-mensual-2026.csv");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        prepared.body().writeTo(out);
        String csv = new String(out.toByteArray(), 3, out.size() - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");

        assertThat(lines).hasSize(14); // header + 12 months + Total
        assertThat(lines[1]).isEqualTo("Enero;100000,00;40000,00;60000,00;60,00");
        assertThat(lines[2]).isEqualTo("Febrero;0,00;0,00;0,00;");
        assertThat(lines[12]).isEqualTo("Diciembre;0,00;0,00;0,00;");
        assertThat(lines[13]).isEqualTo("Total;100000,00;40000,00;60000,00;60,00");
    }

    @Test
    void monthlySummaryComputesIncomePctForMonthsAndAnnualTotal() throws IOException {
        when(analyticsQuery.aggregateByPeriod(
                        eq(familyId),
                        eq(adult.userId()),
                        eq(true),
                        eq(AnalyticsGranularity.MONTH),
                        eq(AnalyticsLens.INCOME_PCT),
                        isNull(),
                        eq(LocalDate.of(2026, 1, 1)),
                        eq(LocalDate.of(2026, 12, 31))))
                .thenReturn(List.of(
                        new PeriodAggregateRow(
                                LocalDate.of(2026, 1, 1),
                                new BigDecimal("100000.0000"),
                                new BigDecimal("25000.0000"),
                                new BigDecimal("100000.0000"),
                                new BigDecimal("25000.0000"),
                                new BigDecimal("25000.0000"),
                                false),
                        new PeriodAggregateRow(
                                LocalDate.of(2026, 2, 1),
                                new BigDecimal("300000.0000"),
                                new BigDecimal("175000.0000"),
                                new BigDecimal("300000.0000"),
                                new BigDecimal("175000.0000"),
                                new BigDecimal("175000.0000"),
                                false)));

        PreparedExport prepared = exportService.prepareMonthlySummaryExport(
                adult, ExportFormat.CSV, null, View.PERSONAL, AnalyticsLens.INCOME_PCT);

        // Default year resolved from fixed clock (2026)
        assertThat(prepared.filename()).isEqualTo("familion-resumen-mensual-2026.csv");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        prepared.body().writeTo(out);
        String csv = new String(out.toByteArray(), 3, out.size() - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");

        assertThat(lines[1]).isEqualTo("Enero;100,00;25,00;75,00;75,00");
        assertThat(lines[2]).isEqualTo("Febrero;100,00;58,33;41,67;41,67");
        assertThat(lines[3]).isEqualTo("Marzo;;;;");
        // Annual total: 200000 / 400000 = 50% expense, 50% balance & savings rate
        assertThat(lines[13]).isEqualTo("Total;100,00;50,00;50,00;50,00");
    }

    @Test
    void rejectsOutOfRangeSummaryYear() {
        assertThatThrownBy(() -> exportService.prepareMonthlySummaryExport(
                        adult, ExportFormat.XLSX, 1800, View.PERSONAL, AnalyticsLens.NOMINAL))
                .isInstanceOf(InvalidExportRequestException.class);
        verifyNoInteractions(analyticsQuery);
    }

    private static ExportTransactionRow sampleRow(String description) {
        return new ExportTransactionRow(
                LocalDate.of(2026, 11, 20),
                TransactionType.EXPENSE,
                description,
                "Hogar",
                "Cuenta Sueldo",
                "Ana Pérez",
                "Ana Pérez",
                "ARS",
                new BigDecimal("1000.0000"),
                new BigDecimal("1000.0000"),
                new BigDecimal("1.0000"),
                new BigDecimal("120.000000"),
                null,
                null,
                TransactionStatus.POSTED);
    }
}
