package com.familion.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_TYPE_BASE = "https://familion.app/problems/";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            NotFoundException ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "not-found", "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND, "not-found", "Not Found", "Resource not found", request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(
            ForbiddenException ex, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return response(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Forbidden",
                "You do not have permission to access this resource",
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        return response(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Unauthorized",
                "Authentication is required to access this resource",
                request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "conflict", "Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn(
                "Data integrity violation on {} {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage());
        return response(
                HttpStatus.CONFLICT,
                "conflict",
                "Conflict",
                "The request conflicts with an existing resource or data constraint",
                request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {
        return response(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Business Rule Violation",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyRequests(
            TooManyRequestsException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(
                HttpStatus.TOO_MANY_REQUESTS,
                "too-many-requests",
                "Too Many Requests",
                ex.getMessage(),
                request);
        long retryAfterSeconds =
                ex.getRetryAfter().isPositive() ? Math.max(1L, ex.getRetryAfter().toSeconds()) : 0L;
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(fe -> errors.add(new FieldErrorDetail(fe.getField(), fe.getDefaultMessage())));
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(ge -> errors.add(new FieldErrorDetail(ge.getObjectName(), ge.getDefaultMessage())));
        return validationResponse("Request validation failed", errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(cv -> new FieldErrorDetail(extractFieldName(cv), cv.getMessage()))
                .toList();
        return validationResponse("Request validation failed", errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        List<FieldErrorDetail> errors =
                List.of(new FieldErrorDetail("body", "Request body is missing or malformed"));
        return validationResponse("Malformed request body", errors, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        List<FieldErrorDetail> errors =
                List.of(new FieldErrorDetail(ex.getParameterName(), "Required parameter is missing"));
        return validationResponse("Request validation failed", errors, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        List<FieldErrorDetail> errors =
                List.of(new FieldErrorDetail(ex.getName(), "Invalid parameter value"));
        return validationResponse("Request validation failed", errors, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return response(
                HttpStatus.METHOD_NOT_ALLOWED,
                "method-not-allowed",
                "Method Not Allowed",
                "HTTP method is not supported for this endpoint",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal Server Error",
                "An unexpected error occurred",
                request);
    }

    private ResponseEntity<ProblemDetail> validationResponse(
            String detail, List<FieldErrorDetail> errors, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(
                HttpStatus.BAD_REQUEST, "validation-error", "Validation Error", detail, request);
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String slug,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = buildProblem(status, slug, title, detail, request);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ProblemDetail buildProblem(
            HttpStatus status,
            String slug,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_TYPE_BASE + slug));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private static String extractFieldName(ConstraintViolation<?> violation) {
        Path propertyPath = violation.getPropertyPath();
        if (propertyPath == null) {
            return "";
        }
        String joined = StreamSupport.stream(propertyPath.spliterator(), false)
                .filter(node -> node.getKind() != ElementKind.METHOD
                        && node.getKind() != ElementKind.CONSTRUCTOR)
                .map(Path.Node::getName)
                .filter(name -> name != null && !name.isBlank())
                .reduce((a, b) -> a + "." + b)
                .orElse("");
        return joined.isEmpty() ? propertyPath.toString() : joined;
    }
}
