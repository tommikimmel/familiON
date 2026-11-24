package com.familion.finance.installments.domain;

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
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "installment_plans")
public class InstallmentPlan extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "credit_card_account_id", nullable = false, updatable = false)
    private UUID creditCardAccountId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(name = "description", nullable = false, length = 200, updatable = false)
    private String description;

    @Column(name = "currency_code", nullable = false, length = 10, updatable = false)
    private String currencyCode;

    @Column(name = "total_installments", nullable = false, updatable = false)
    private short totalInstallments;

    @Column(name = "has_interest", nullable = false, updatable = false)
    private boolean hasInterest;

    @Column(name = "cash_price", precision = 19, scale = 4, updatable = false)
    private BigDecimal cashPrice;

    @Column(name = "financed_total", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal financedTotal;

    @Column(
            name = "installment_amount",
            nullable = false,
            precision = 19,
            scale = 4,
            updatable = false)
    private BigDecimal installmentAmount;

    @Column(name = "purchase_date", nullable = false, updatable = false)
    private LocalDate purchaseDate;

    @Column(name = "first_billing_date", nullable = false, updatable = false)
    private LocalDate firstBillingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InstallmentPlanStatus status;

    protected InstallmentPlan() {}

    private InstallmentPlan(
            UUID familyId,
            UUID userId,
            UUID creditCardAccountId,
            UUID categoryId,
            String description,
            String currencyCode,
            int totalInstallments,
            boolean hasInterest,
            BigDecimal cashPrice,
            BigDecimal financedTotal,
            BigDecimal installmentAmount,
            LocalDate purchaseDate,
            LocalDate firstBillingDate,
            InstallmentPlanStatus status) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.creditCardAccountId =
                Objects.requireNonNull(creditCardAccountId, "creditCardAccountId must not be null");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
        this.description =
                Objects.requireNonNull(description, "description must not be null").trim();
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null")
                .trim()
                .toUpperCase(Locale.ROOT);
        this.totalInstallments = (short) totalInstallments;
        this.hasInterest = hasInterest;
        this.cashPrice = cashPrice == null ? null : Money.scale(cashPrice);
        this.financedTotal = Money.scale(financedTotal);
        this.installmentAmount = Money.scale(installmentAmount);
        this.purchaseDate = Objects.requireNonNull(purchaseDate, "purchaseDate must not be null");
        this.firstBillingDate =
                Objects.requireNonNull(firstBillingDate, "firstBillingDate must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static InstallmentPlan create(
            UUID familyId,
            UUID userId,
            UUID creditCardAccountId,
            UUID categoryId,
            String description,
            String currencyCode,
            int totalInstallments,
            boolean hasInterest,
            BigDecimal cashPrice,
            BigDecimal financedTotal,
            BigDecimal installmentAmount,
            LocalDate purchaseDate,
            LocalDate firstBillingDate,
            InstallmentPlanStatus status) {
        return new InstallmentPlan(
                familyId,
                userId,
                creditCardAccountId,
                categoryId,
                description,
                currencyCode,
                totalInstallments,
                hasInterest,
                cashPrice,
                financedTotal,
                installmentAmount,
                purchaseDate,
                firstBillingDate,
                status);
    }

    public boolean isActive() {
        return this.status == InstallmentPlanStatus.ACTIVE;
    }

    public void markCompleted() {
        this.status = InstallmentPlanStatus.COMPLETED;
    }

    public void markCancelled() {
        this.status = InstallmentPlanStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCreditCardAccountId() {
        return creditCardAccountId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getDescription() {
        return description;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public int getTotalInstallments() {
        return totalInstallments;
    }

    public boolean isHasInterest() {
        return hasInterest;
    }

    public BigDecimal getCashPrice() {
        return cashPrice;
    }

    public BigDecimal getFinancedTotal() {
        return financedTotal;
    }

    public BigDecimal getInstallmentAmount() {
        return installmentAmount;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public LocalDate getFirstBillingDate() {
        return firstBillingDate;
    }

    public InstallmentPlanStatus getStatus() {
        return status;
    }
}
