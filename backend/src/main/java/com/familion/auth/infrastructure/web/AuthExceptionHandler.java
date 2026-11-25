package com.familion.auth.infrastructure.web;

import com.familion.auth.domain.AuthUnauthorizedException;
import com.familion.family.application.api.InvalidAvatarException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.familion.auth.infrastructure.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    private static final URI TYPE_UNAUTHORIZED =
            URI.create("https://familion.app/problems/unauthorized");

    @ExceptionHandler(AuthUnauthorizedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(
            AuthUnauthorizedException ex, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Unauthorized");
        problem.setType(TYPE_UNAUTHORIZED);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(InvalidAvatarException.class)
    public ResponseEntity<ProblemDetail> handleInvalidAvatar(
            InvalidAvatarException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.getReason()) {
            case EMPTY -> HttpStatus.BAD_REQUEST;
            case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://familion.app/problems/invalid-avatar"));
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
