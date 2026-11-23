package com.familion.finance.accounts.domain;

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
@Table(name = "accounts")
public class Account extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "currency_code", nullable = false, length = 10, updatable = false)
    private String currencyCode;

    @Column(name = "initial_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialBalance;

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "closing_day")
    private Short closingDay;

    @Column(name = "due_day")
    private Short dueDay;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Account() {}

    private Account(
            UUID familyId,
            UUID ownerUserId,
            String name,
            AccountType accountType,
            String currencyCode,
            BigDecimal initialBalance,
            BigDecimal creditLimit,
            Integer closingDay,
            Integer dueDay) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.ownerUserId = ownerUserId;
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.accountType = Objects.requireNonNull(accountType, "accountType must not be null");
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        this.initialBalance = Money.scale(
                initialBalance == null ? BigDecimal.ZERO : initialBalance);
        this.creditLimit = creditLimit == null ? null : Money.scale(creditLimit);
        this.closingDay = toShort(closingDay);
        this.dueDay = toShort(dueDay);
        this.active = true;
        this.deletedAt = null;
    }

    public static Account create(
            UUID familyId,
            UUID ownerUserId,
            String name,
            AccountType accountType,
            String currencyCode,
            BigDecimal initialBalance,
            BigDecimal creditLimit,
            Integer closingDay,
            Integer dueDay) {
        return new Account(
                familyId,
                ownerUserId,
                name,
                accountType,
                currencyCode,
                initialBalance,
                creditLimit,
                closingDay,
                dueDay);
    }

    public void update(
            String name,
            AccountType accountType,
            BigDecimal initialBalance,
            UUID ownerUserId,
            BigDecimal creditLimit,
            Integer closingDay,
            Integer dueDay) {
        this.name = Objects.requireNonNull(name, "name must not be null").trim();
        this.accountType = Objects.requireNonNull(accountType, "accountType must not be null");
        this.initialBalance = Money.scale(initialBalance);
        this.ownerUserId = ownerUserId;
        this.creditLimit = creditLimit == null ? null : Money.scale(creditLimit);
        this.closingDay = toShort(closingDay);
        this.dueDay = toShort(dueDay);
    }

    public void softDelete(Instant now) {
        this.deletedAt = Objects.requireNonNull(now, "now must not be null");
        this.active = false;
    }

    public boolean isOwnedBy(UUID userId) {
        return userId != null && userId.equals(this.ownerUserId);
    }

    public boolean isUsable() {
        return this.active && this.deletedAt == null;
    }

    private static Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
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

    public String getName() {
        return name;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public Integer getClosingDay() {
        return closingDay == null ? null : closingDay.intValue();
    }

    public Integer getDueDay() {
        return dueDay == null ? null : dueDay.intValue();
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
