package com.familion.economics.infrastructure.web;

import com.familion.common.error.FieldErrorDetail;
import com.familion.economics.application.EconomicsIndexService;
import com.familion.economics.application.IndexEntry;
import com.familion.economics.application.InvalidEconomicsQueryException;
import com.familion.economics.domain.IndexCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for querying deployment-wide economic indices and recording manual overrides.
 */
@RestController
@RequestMapping("/api/v1/economics")
public class EconomicsController {

    private static final URI VALIDATION_ERROR_TYPE =
            URI.create("https://familion.app/problems/validation-error");

    private final EconomicsIndexService indexService;

    public EconomicsController(EconomicsIndexService indexService) {
        this.indexService = indexService;
    }

    @GetMapping("/indices/latest")
    public List<IndexValueResponse> getLatestIndices() {
        return indexService.getLatestAll().stream().map(IndexValueResponse::from).toList();
    }

    @GetMapping("/indices/history")
    public List<IndexValueResponse> getIndexHistory(
            @RequestParam IndexCode indexCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return indexService.getHistory(indexCode, from, to).stream()
                .map(IndexValueResponse::from)
                .toList();
    }

    /**
     * Manually upserts a deployment-wide index value. Restricted to {@code ADULTO} users.
     */
    @PostMapping("/indices/manual")
    @PreAuthorize("hasRole('ADULTO')")
    public ResponseEntity<IndexValueResponse> createManualIndex(
            @Valid @RequestBody ManualIndexRequest request) {
        IndexEntry saved = indexService.saveManual(
                request.indexCode(), request.effectiveDate(), request.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(IndexValueResponse.from(saved));
    }

    @ExceptionHandler(InvalidEconomicsQueryException.class)
    public ResponseEntity<ProblemDetail> handleInvalidQuery(
            InvalidEconomicsQueryException ex, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Validation Error");
        problem.setType(VALIDATION_ERROR_TYPE);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", List.of(new FieldErrorDetail(ex.getField(), ex.getMessage())));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
