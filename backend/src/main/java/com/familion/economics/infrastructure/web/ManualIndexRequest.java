package com.familion.economics.infrastructure.web;

import com.familion.economics.domain.IndexCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ManualIndexRequest(
        @NotNull(message = "indexCode is required") IndexCode indexCode,
        @NotNull(message = "effectiveDate is required") LocalDate effectiveDate,
        @NotNull(message = "value is required")
                @DecimalMin(value = "0.0", inclusive = false, message = "value must be greater than 0")
                BigDecimal value) {}
