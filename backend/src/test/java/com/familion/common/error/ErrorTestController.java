package com.familion.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/test/errors")
class ErrorTestController {

    record SampleRequest(@NotBlank String name, @Min(1) int quantity) {}

    @GetMapping("/not-found")
    void notFound() {
        throw new NotFoundException("Account not found");
    }

    @GetMapping("/forbidden")
    void forbidden() {
        throw new ForbiddenException("Family view is not permitted");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ConflictException("Email already registered");
    }

    @GetMapping("/business-rule")
    void businessRule() {
        throw new BusinessRuleException("Insufficient balance");
    }

    @GetMapping("/too-many-requests")
    void tooManyRequests() {
        throw new TooManyRequestsException("Too many attempts", Duration.ofSeconds(45));
    }

    @PostMapping("/validation")
    SampleRequest validation(@Valid @RequestBody SampleRequest request) {
        return request;
    }

    @GetMapping("/constraint")
    int constraint(@RequestParam @Min(10) int limit) {
        return limit;
    }

    @GetMapping("/data-integrity")
    void dataIntegrity() {
        throw new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"uq_users_email\" Detail: Key (email)=(secret@example.com) already exists.");
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException("Sensitive internal database failure details");
    }
}
