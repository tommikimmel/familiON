package com.familion.analytics.exports.infrastructure.web;

import com.familion.analytics.domain.AnalyticsLens;
import com.familion.analytics.exports.application.ExportService;
import com.familion.analytics.exports.application.PreparedExport;
import com.familion.analytics.exports.domain.ExportFormat;
import com.familion.common.security.CurrentUserProvider;
import com.familion.common.security.View;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final ExportService exportService;
    private final CurrentUserProvider currentUserProvider;

    public ExportController(
            ExportService exportService, CurrentUserProvider currentUserProvider) {
        this.exportService = exportService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/transactions")
    public ResponseEntity<StreamingResponseBody> exportTransactions(
            @RequestParam(defaultValue = "CSV") ExportFormat format,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "PERSONAL") View view,
            @RequestParam(defaultValue = "NOMINAL") AnalyticsLens lens) {
        PreparedExport export = exportService.prepareTransactionsExport(
                currentUserProvider.get(), format, from, to, view, lens);
        return toResponse(export);
    }

    @GetMapping("/monthly-summary")
    public ResponseEntity<StreamingResponseBody> exportMonthlySummary(
            @RequestParam(defaultValue = "CSV") ExportFormat format,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "PERSONAL") View view,
            @RequestParam(defaultValue = "NOMINAL") AnalyticsLens lens) {
        PreparedExport export = exportService.prepareMonthlySummaryExport(
                currentUserProvider.get(), format, year, view, lens);
        return toResponse(export);
    }

    private static ResponseEntity<StreamingResponseBody> toResponse(PreparedExport export) {
        String contentDisposition =
                ContentDisposition.attachment().filename(export.filename()).build().toString();
        return ResponseEntity.ok()
                .contentType(export.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(export.body());
    }
}
