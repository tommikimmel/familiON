package com.familion.bff.error;

import com.familion.bff.core.CoreClientException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ProblemDetail> handleCoreUnavailable(
            ResourceAccessException ex, HttpServletRequest request) {
        log.warn("Core API unavailable for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "Unable to reach the core service.");
        problem.setTitle("Core unavailable");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(CoreClientException.class)
    public ResponseEntity<String> handleCoreClientException(
            CoreClientException ex, HttpServletRequest request) {
        String body = ex.problemJson();
        if (body != null && !body.isBlank()) {
            return ResponseEntity.status(ex.status())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(body);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(ex.status()), ex.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(ex.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"type\":\"about:blank\",\"title\":\"" + problem.getTitle()
                        + "\",\"status\":" + ex.status() + "}");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Invalid request payload.");
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled BFF error for {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
