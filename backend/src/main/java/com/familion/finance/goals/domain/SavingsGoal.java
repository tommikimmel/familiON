package com.familion.finance.goals.domain;

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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "savings_goals")
public class SavingsGoal extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "owner_user_id", updatable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 15, updatable = false)
    private GoalScope scope;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_unit", nullable = false, length = 15, updatable = false)
    private TargetUnit targetUnit;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GoalStatus status;

    protected SavingsGoal() {}

    private SavingsGoal(
            UUID familyId,
            UUID ownerUserId,
            GoalScope scope,
            String name,
            BigDecimal targetAmount,
            TargetUnit targetUnit,
            LocalDate targetDate,
            Instant createdAt) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        if (scope == GoalScope.FAMILY && ownerUserId != null) {
            throw new IllegalArgumentException("Family savings goal must not have an owner");
        }
        if (scope == GoalScope.PERSONAL && ownerUserId == null) {
            throw new IllegalArgumentException("Personal savings goal must have an owner");
        }
        this.ownerUserId = ownerUserId;
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.targetAmount = Money.scale(
                Objects.requireNonNull(targetAmount, "targetAmount must not be null"));
        this.targetUnit = Objects.requireNonNull(targetUnit, "targetUnit must not be null");
        this.targetDate = targetDate;
        this.status = GoalStatus.ACTIVE;
        if (createdAt != null) {
            setCreatedAt(createdAt);
            setUpdatedAt(createdAt);
        }
    }

    public static SavingsGoal create(
            UUID familyId,
            UUID ownerUserId,
            GoalScope scope,
            String name,
            BigDecimal targetAmount,
            TargetUnit targetUnit,
            LocalDate targetDate,
            Instant createdAt) {
        return new SavingsGoal(
                familyId, ownerUserId, scope, name, targetAmount, targetUnit, targetDate, createdAt);
    }

    public void update(
            String name, BigDecimal targetAmount, LocalDate targetDate, GoalStatus status) {
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.targetAmount = Money.scale(
                Objects.requireNonNull(targetAmount, "targetAmount must not be null"));
        this.targetDate = targetDate;
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public void changeStatus(GoalStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "newStatus must not be null");
    }

    public boolean isOwnedBy(UUID userId) {
        return userId != null && userId.equals(this.ownerUserId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public GoalScope getScope() {
        return scope;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public TargetUnit getTargetUnit() {
        return targetUnit;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public GoalStatus getStatus() {
        return status;
    }
}
