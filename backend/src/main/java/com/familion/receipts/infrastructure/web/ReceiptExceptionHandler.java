package com.familion.receipts.infrastructure.web;

import com.familion.common.error.FieldErrorDetail;
import com.familion.receipts.domain.EmptyReceiptFileException;
import com.familion.receipts.domain.ReceiptTooLargeException;
import com.familion.receipts.domain.UnsupportedReceiptMediaTypeException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReceiptExceptionHandler {

    private static final String PROBLEM_TYPE_BASE = "https://familion.app/problems/";

    @ExceptionHandler(ReceiptTooLargeException.class)
    public ResponseEntity<ProblemDetail> handleTooLarge(
            ReceiptTooLargeException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "payload-too-large",
                "Payload Too Large",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "payload-too-large",
                "Payload Too Large",
                "Receipt file exceeds the maximum allowed size of 5 MB",
                request);
    }

    @ExceptionHandler(UnsupportedReceiptMediaTypeException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
            UnsupportedReceiptMediaTypeException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "unsupported-media-type",
                "Unsupported Media Type",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(EmptyReceiptFileException.class)
    public ResponseEntity<ProblemDetail> handleEmptyFile(
            EmptyReceiptFileException ex, HttpServletRequest request) {
        return validationError("file", ex.getMessage(), request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ProblemDetail> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        return validationError(ex.getRequestPartName(), "Required file part is missing", request);
    }

    private ResponseEntity<ProblemDetail> validationError(
            String field, String message, HttpServletRequest request) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        detail.setTitle("Validation Error");
        detail.setType(URI.create(PROBLEM_TYPE_BASE + "validation-error"));
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("errors", List.of(new FieldErrorDetail(field, message)));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(detail);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String slug,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_TYPE_BASE + slug));
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
