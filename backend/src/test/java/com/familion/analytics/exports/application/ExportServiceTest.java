package com.familion.analytics.exports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.exports.domain.ExportFormat;
import com.familion.analytics.exports.domain.InvalidExportRequestException;
import com.familion.analytics.exports.infrastructure.format.CsvExportWriter;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExportServiceTest {

    private ExportQuery exportQuery;
    private FamilyQueryApi familyQueryApi;
    private EconomicsApi economicsApi;
    private ExportService exportService;

    private UUID familyId;
    private CurrentUser adult;
    private CurrentUser childOwnOnly;

    @BeforeEach
    void setUp() {
        exportQuery = mock(ExportQuery.class);
        familyQueryApi = mock(FamilyQueryApi.class);
        economicsApi = mock(EconomicsApi.class);
        exportService = new ExportService(
                exportQuery,
                new DefaultVisibilityPolicy(),
                familyQueryApi,
                economicsApi,
                List.of(new CsvExportWriter()),
                2);

        familyId = UUID.randomUUID();
        adult = new CurrentUser(UUID.randomUUID(), familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
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
    void streamsTransactionsInPagesUntilPartialPage() throws IOException {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);

        when(exportQuery.findTransactionPage(eq(familyId), eq(adult.userId()), eq(from), eq(to), eq(2), eq(0L)))
                .thenReturn(List.of(sampleRow("Tx 1"), sampleRow("Tx 2")));
        when(exportQuery.findTransactionPage(eq(familyId), eq(adult.userId()), eq(from), eq(to), eq(2), eq(2L)))
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

        verify(exportQuery)
                .findTransactionPage(familyId, adult.userId(), from, to, 2, 0L);
        verify(exportQuery)
                .findTransactionPage(familyId, adult.userId(), from, to, 2, 2L);
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
