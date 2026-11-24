package com.familion.finance.goals.domain;

import com.familion.common.money.Money;
import com.familion.common.persistence.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "goal_contributions")
public class GoalContribution extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "savings_goal_id", nullable = false, updatable = false)
    private UUID savingsGoalId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "transaction_id", updatable = false)
    private UUID transactionId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 10, updatable = false)
    private String currencyCode;

    @Column(
            name = "amount_in_goal_unit",
            nullable = false,
            precision = 19,
            scale = 4,
            updatable = false)
    private BigDecimal amountInGoalUnit;

    @Column(name = "contribution_date", nullable = false, updatable = false)
    private LocalDate contributionDate;

    @Column(name = "notes", length = 255, updatable = false)
    private String notes;

    protected GoalContribution() {}

    private GoalContribution(
            UUID savingsGoalId,
            UUID userId,
            UUID transactionId,
            BigDecimal amount,
            String currencyCode,
            BigDecimal amountInGoalUnit,
            LocalDate contributionDate,
            String notes,
            Instant createdAt) {
        this.savingsGoalId =
                Objects.requireNonNull(savingsGoalId, "savingsGoalId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.transactionId = transactionId;
        this.amount = Money.scale(Objects.requireNonNull(amount, "amount must not be null"));
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null")
                .trim()
                .toUpperCase(Locale.ROOT);
        this.amountInGoalUnit = Money.scale(
                Objects.requireNonNull(amountInGoalUnit, "amountInGoalUnit must not be null"));
        this.contributionDate =
                Objects.requireNonNull(contributionDate, "contributionDate must not be null");
        this.notes = notes;
        if (createdAt != null) {
            setCreatedAt(createdAt);
        }
    }

    public static GoalContribution create(
            UUID savingsGoalId,
            UUID userId,
            UUID transactionId,
            BigDecimal amount,
            String currencyCode,
            BigDecimal amountInGoalUnit,
            LocalDate contributionDate,
            String notes,
            Instant createdAt) {
        return new GoalContribution(
                savingsGoalId,
                userId,
                transactionId,
                amount,
                currencyCode,
                amountInGoalUnit,
                contributionDate,
                notes,
                createdAt);
    }

    public boolean isAuthoredBy(UUID userId) {
        return userId != null && userId.equals(this.userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSavingsGoalId() {
        return savingsGoalId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getAmountInGoalUnit() {
        return amountInGoalUnit;
    }

    public LocalDate getContributionDate() {
        return contributionDate;
    }

    public String getNotes() {
        return notes;
    }
}
