package com.familion.family.domain;

import com.familion.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "families")
public class Family extends AuditableEntity {

    public static final String DEFAULT_BASE_CURRENCY = "ARS";
    public static final String DEFAULT_REFERENCE_CURRENCY = "USD";
    public static final String DEFAULT_REFERENCE_RATE_SOURCE = "USD_MEP";
    public static final String DEFAULT_TIMEZONE = "America/Argentina/Buenos_Aires";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "base_currency", nullable = false, length = 10)
    private String baseCurrency;

    @Column(name = "reference_currency", nullable = false, length = 10)
    private String referenceCurrency;

    @Column(name = "reference_rate_source", nullable = false, length = 30)
    private String referenceRateSource;

    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone;

    protected Family() {}

    private Family(
            String name,
            String baseCurrency,
            String referenceCurrency,
            String referenceRateSource,
            String timezone) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.baseCurrency = Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        this.referenceCurrency =
                Objects.requireNonNull(referenceCurrency, "referenceCurrency must not be null");
        this.referenceRateSource =
                Objects.requireNonNull(referenceRateSource, "referenceRateSource must not be null");
        this.timezone = Objects.requireNonNull(timezone, "timezone must not be null");
    }

    public static Family createDefault(String name) {
        return new Family(
                name,
                DEFAULT_BASE_CURRENCY,
                DEFAULT_REFERENCE_CURRENCY,
                DEFAULT_REFERENCE_RATE_SOURCE,
                DEFAULT_TIMEZONE);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getReferenceCurrency() {
        return referenceCurrency;
    }

    public String getReferenceRateSource() {
        return referenceRateSource;
    }

    public String getTimezone() {
        return timezone;
    }
}
