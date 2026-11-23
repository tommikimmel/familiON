package com.familion.family.domain;

import com.familion.common.persistence.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "family_currencies")
public class FamilyCurrency extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "preferred_rate_source", length = 30)
    private String preferredRateSource;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected FamilyCurrency() {}

    private FamilyCurrency(
            UUID familyId, String currencyCode, String preferredRateSource, boolean active) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        this.preferredRateSource = preferredRateSource;
        this.active = active;
    }

    public static FamilyCurrency create(
            UUID familyId, String currencyCode, String preferredRateSource) {
        return new FamilyCurrency(familyId, currencyCode, preferredRateSource, true);
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getPreferredRateSource() {
        return preferredRateSource;
    }

    public boolean isActive() {
        return active;
    }
}
