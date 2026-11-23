package com.familion.economics.infrastructure.external;

import com.familion.economics.domain.IndexCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record ExternalIndexPoint(
        IndexCode indexCode, LocalDate effectiveDate, BigDecimal value) {

    public ExternalIndexPoint {
        Objects.requireNonNull(indexCode, "indexCode must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
