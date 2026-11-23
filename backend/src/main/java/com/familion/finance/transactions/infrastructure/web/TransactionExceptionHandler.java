package com.familion.finance.transactions.infrastructure.web;

import com.familion.common.error.FieldErrorDetail;
import com.familion.finance.transactions.application.InvalidTransactionRequestException;
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

@RestControllerAdvice(basePackages = "com.familion.finance.transactions.infrastructure.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TransactionExceptionHandler {

    private static final URI VALIDATION_ERROR_TYPE =
            URI.create("https://familion.app/problems/validation-error");

    @ExceptionHandler(InvalidTransactionRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTransactionRequest(
            InvalidTransactionRequestException ex, HttpServletRequest request) {
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
