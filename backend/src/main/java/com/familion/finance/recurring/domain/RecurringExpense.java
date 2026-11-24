package com.familion.finance.recurring.domain;

import com.familion.common.money.Money;
import com.familion.common.persistence.AuditableEntity;
import com.familion.finance.transactions.application.api.Scope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "recurring_expenses")
public class RecurringExpense extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 15)
    private Scope scope;

    @Column(name = "base_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseAmount;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "due_day", nullable = false)
    private short dueDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_rule", nullable = false, length = 25)
    private AdjustmentRule adjustmentRule;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected RecurringExpense() {}

    private RecurringExpense(
            UUID familyId,
            UUID createdByUserId,
            UUID accountId,
            UUID categoryId,
            String name,
            Scope scope,
            BigDecimal baseAmount,
            String currencyCode,
            int dueDay,
            AdjustmentRule adjustmentRule,
            LocalDate startDate,
            LocalDate endDate) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.createdByUserId =
                Objects.requireNonNull(createdByUserId, "createdByUserId must not be null");
        this.accountId = accountId;
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.baseAmount = Money.scale(Objects.requireNonNull(baseAmount, "baseAmount must not be null"));
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        this.dueDay = (short) dueDay;
        this.adjustmentRule =
                Objects.requireNonNull(adjustmentRule, "adjustmentRule must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.endDate = endDate;
        this.active = true;
    }

    public static RecurringExpense create(
            UUID familyId,
            UUID createdByUserId,
            UUID accountId,
            UUID categoryId,
            String name,
            Scope scope,
            BigDecimal baseAmount,
            String currencyCode,
            int dueDay,
            AdjustmentRule adjustmentRule,
            LocalDate startDate,
            LocalDate endDate) {
        return new RecurringExpense(
                familyId,
                createdByUserId,
                accountId,
                categoryId,
                name,
                scope,
                baseAmount,
                currencyCode,
                dueDay,
                adjustmentRule,
                startDate,
                endDate);
    }

    public void update(
            String name,
            UUID categoryId,
            UUID accountId,
            Scope scope,
            BigDecimal baseAmount,
            String currencyCode,
            int dueDay,
            AdjustmentRule adjustmentRule,
            LocalDate startDate,
            LocalDate endDate,
            boolean active) {
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
        this.accountId = accountId;
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.baseAmount = Money.scale(Objects.requireNonNull(baseAmount, "baseAmount must not be null"));
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        this.dueDay = (short) dueDay;
        this.adjustmentRule =
                Objects.requireNonNull(adjustmentRule, "adjustmentRule must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.endDate = endDate;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isCreatedBy(UUID userId) {
        return userId != null && userId.equals(this.createdByUserId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public Scope getScope() {
        return scope;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public int getDueDay() {
        return dueDay;
    }

    public AdjustmentRule getAdjustmentRule() {
        return adjustmentRule;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isActive() {
        return active;
    }
}
