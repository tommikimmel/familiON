package com.familion.economics.infrastructure.web;

import com.familion.economics.application.IndexEntry;
import com.familion.economics.domain.IndexSource;
import java.math.BigDecimal;
import java.time.LocalDate;

public record IndexValueResponse(
        String indexCode, LocalDate effectiveDate, BigDecimal value, IndexSource source) {

    public static IndexValueResponse from(IndexEntry entry) {
        return new IndexValueResponse(
                entry.indexCode().name(),
                entry.effectiveDate(),
                entry.value(),
                entry.source());
    }
}
