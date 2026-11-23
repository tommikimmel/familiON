package com.familion.economics.application;

import com.familion.economics.domain.EconomicIndex;
import com.familion.economics.domain.IndexCode;
import com.familion.economics.domain.IndexSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record IndexEntry(
        IndexCode indexCode, LocalDate effectiveDate, BigDecimal value, IndexSource source) {

    public IndexEntry {
        Objects.requireNonNull(indexCode, "indexCode must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(source, "source must not be null");
    }

    public static IndexEntry fromEntity(EconomicIndex entity) {
        return new IndexEntry(
                entity.getIndexCode(),
                entity.getEffectiveDate(),
                entity.getValue(),
                entity.getSource());
    }
}
