package com.familion.family.allowances.infrastructure.web;

import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DisburseAllowanceRequest(
        LocalDate date,
        @Positive(message = "Amount must be greater than zero") BigDecimal amount) {}
