package com.familion.economics.domain;

import com.familion.common.money.Money;
import com.familion.common.persistence.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Global (deployment-wide) economic quote or inflation index entry for a given effective date.
 */
@Entity
@Table(
        name = "economic_indices",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_economic_index_code_date",
                        columnNames = {"index_code", "effective_date"}))
public class EconomicIndex extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "index_code", nullable = false, length = 30)
    private IndexCode indexCode;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "value", nullable = false, precision = 19, scale = 6)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private IndexSource source;

    protected EconomicIndex() {}

    public EconomicIndex(
            IndexCode indexCode, LocalDate effectiveDate, BigDecimal value, IndexSource source) {
        this.indexCode = Objects.requireNonNull(indexCode, "indexCode must not be null");
        this.effectiveDate =
                Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        BigDecimal scaled = Money.rate(Objects.requireNonNull(value, "value must not be null"));
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("value must be greater than zero");
        }
        this.value = scaled;
        this.source = Objects.requireNonNull(source, "source must not be null");
    }

    public UUID getId() {
        return id;
    }

    public IndexCode getIndexCode() {
        return indexCode;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public BigDecimal getValue() {
        return value;
    }

    public IndexSource getSource() {
        return source;
    }
}
