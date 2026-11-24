package com.familion.family.allowances.domain;

import com.familion.common.money.Money;
import com.familion.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "allowances")
public class Allowance extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "assigned_by_user_id", nullable = false, updatable = false)
    private UUID assignedByUserId;

    @Column(name = "child_user_id", nullable = false)
    private UUID childUserId;

    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    @Column(name = "target_account_id", nullable = false)
    private UUID targetAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 15)
    private AllowanceFrequency frequency;

    @Column(name = "day_of_period", nullable = false)
    private short dayOfPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_rule", nullable = false, length = 25)
    private AllowanceAdjustmentRule adjustmentRule;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Allowance() {}

    private Allowance(
            UUID familyId,
            UUID assignedByUserId,
            UUID childUserId,
            UUID sourceAccountId,
            UUID targetAccountId,
            BigDecimal amount,
            String currencyCode,
            AllowanceFrequency frequency,
            int dayOfPeriod,
            AllowanceAdjustmentRule adjustmentRule,
            Instant createdAt) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.assignedByUserId =
                Objects.requireNonNull(assignedByUserId, "assignedByUserId must not be null");
        this.childUserId = Objects.requireNonNull(childUserId, "childUserId must not be null");
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId =
                Objects.requireNonNull(targetAccountId, "targetAccountId must not be null");
        this.amount = Money.scale(Objects.requireNonNull(amount, "amount must not be null"));
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        this.frequency = Objects.requireNonNull(frequency, "frequency must not be null");
        this.dayOfPeriod = (short) dayOfPeriod;
        this.adjustmentRule =
                Objects.requireNonNull(adjustmentRule, "adjustmentRule must not be null");
        this.active = true;
        if (createdAt != null) {
            setCreatedAt(createdAt);
            setUpdatedAt(createdAt);
        }
    }

    public static Allowance create(
            UUID familyId,
            UUID assignedByUserId,
            UUID childUserId,
            UUID sourceAccountId,
            UUID targetAccountId,
            BigDecimal amount,
            String currencyCode,
            AllowanceFrequency frequency,
            int dayOfPeriod,
            AllowanceAdjustmentRule adjustmentRule,
            Instant createdAt) {
        return new Allowance(
                familyId,
                assignedByUserId,
                childUserId,
                sourceAccountId,
                targetAccountId,
                amount,
                currencyCode,
                frequency,
                dayOfPeriod,
                adjustmentRule,
                createdAt);
    }

    public void update(
            UUID childUserId,
            UUID sourceAccountId,
            UUID targetAccountId,
            BigDecimal amount,
            String currencyCode,
            AllowanceFrequency frequency,
            int dayOfPeriod,
            AllowanceAdjustmentRule adjustmentRule,
            boolean active) {
        this.childUserId = Objects.requireNonNull(childUserId, "childUserId must not be null");
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId =
                Objects.requireNonNull(targetAccountId, "targetAccountId must not be null");
        this.amount = Money.scale(Objects.requireNonNull(amount, "amount must not be null"));
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        this.frequency = Objects.requireNonNull(frequency, "frequency must not be null");
        this.dayOfPeriod = (short) dayOfPeriod;
        this.adjustmentRule =
                Objects.requireNonNull(adjustmentRule, "adjustmentRule must not be null");
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getAssignedByUserId() {
        return assignedByUserId;
    }

    public UUID getChildUserId() {
        return childUserId;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public AllowanceFrequency getFrequency() {
        return frequency;
    }

    public int getDayOfPeriod() {
        return dayOfPeriod;
    }

    public AllowanceAdjustmentRule getAdjustmentRule() {
        return adjustmentRule;
    }

    public boolean isActive() {
        return active;
    }
}
