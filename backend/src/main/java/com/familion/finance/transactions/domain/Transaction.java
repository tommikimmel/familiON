package com.familion.finance.transactions.domain;

import com.familion.common.money.Money;
import com.familion.common.persistence.AuditableEntity;
import com.familion.finance.transactions.application.api.Scope;
import com.familion.finance.transactions.application.api.TransactionStatus;
import com.familion.finance.transactions.application.api.TransactionType;
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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "attributed_to_user_id", nullable = false)
    private UUID attributedToUserId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 15)
    private Scope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private TransactionStatus status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 10, updatable = false)
    private String currencyCode;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "notes")
    private String notes;

    @Column(name = "installment_plan_id", updatable = false)
    private UUID installmentPlanId;

    @Column(name = "installment_number", updatable = false)
    private Short installmentNumber;

    @Column(name = "recurring_expense_id", updatable = false)
    private UUID recurringExpenseId;

    @Column(name = "currency_exchange_id", updatable = false)
    private UUID currencyExchangeId;

    @Column(name = "allowance_id", updatable = false)
    private UUID allowanceId;

    @Column(name = "receipt_attachment_id")
    private UUID receiptAttachmentId;

    @Column(name = "transfer_group_id", updatable = false)
    private UUID transferGroupId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Transaction() {}

    private Transaction(
            UUID familyId,
            UUID userId,
            UUID attributedToUserId,
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            Scope scope,
            TransactionStatus status,
            BigDecimal amount,
            String currencyCode,
            LocalDate transactionDate,
            String description,
            String notes,
            UUID installmentPlanId,
            Integer installmentNumber,
            UUID recurringExpenseId,
            UUID currencyExchangeId,
            UUID allowanceId,
            UUID receiptAttachmentId,
            UUID transferGroupId) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.attributedToUserId =
                Objects.requireNonNull(attributedToUserId, "attributedToUserId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.categoryId = categoryId;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.amount = Money.scale(Objects.requireNonNull(amount, "amount must not be null"));
        this.currencyCode = normalizeCurrency(currencyCode);
        this.transactionDate =
                Objects.requireNonNull(transactionDate, "transactionDate must not be null");
        this.description = normalizeOptionalText(description);
        this.notes = normalizeOptionalText(notes);
        this.installmentPlanId = installmentPlanId;
        this.installmentNumber = installmentNumber == null ? null : installmentNumber.shortValue();
        this.recurringExpenseId = recurringExpenseId;
        this.currencyExchangeId = currencyExchangeId;
        this.allowanceId = allowanceId;
        this.receiptAttachmentId = receiptAttachmentId;
        this.transferGroupId = transferGroupId;
        this.deletedAt = null;
    }

    public static Transaction create(
            UUID familyId,
            UUID userId,
            UUID attributedToUserId,
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            Scope scope,
            TransactionStatus status,
            BigDecimal amount,
            String currencyCode,
            LocalDate transactionDate,
            String description,
            String notes,
            UUID installmentPlanId,
            Integer installmentNumber,
            UUID recurringExpenseId,
            UUID currencyExchangeId,
            UUID allowanceId,
            UUID receiptAttachmentId,
            UUID transferGroupId) {
        return new Transaction(
                familyId,
                userId,
                attributedToUserId,
                accountId,
                categoryId,
                type,
                scope,
                status,
                amount,
                currencyCode,
                transactionDate,
                description,
                notes,
                installmentPlanId,
                installmentNumber,
                recurringExpenseId,
                currencyExchangeId,
                allowanceId,
                receiptAttachmentId,
                transferGroupId);
    }

    public void updateAmountAndDate(BigDecimal amount, LocalDate transactionDate) {
        this.amount = Money.scale(Objects.requireNonNull(amount, "amount must not be null"));
        this.transactionDate =
                Objects.requireNonNull(transactionDate, "transactionDate must not be null");
    }

    public void updateAmount(BigDecimal amount) {
        this.amount = Money.scale(Objects.requireNonNull(amount, "amount must not be null"));
    }

    public void updateTransactionDate(LocalDate transactionDate) {
        this.transactionDate =
                Objects.requireNonNull(transactionDate, "transactionDate must not be null");
    }

    public void updateAccountId(UUID accountId) {
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
    }

    public void updateCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public void updateDescription(String description) {
        this.description = normalizeOptionalText(description);
    }

    public void updateNotes(String notes) {
        this.notes = normalizeOptionalText(notes);
    }

    public void updateAttributedToUserId(UUID attributedToUserId) {
        this.attributedToUserId =
                Objects.requireNonNull(attributedToUserId, "attributedToUserId must not be null");
    }

    public void updateScope(Scope scope) {
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
    }

    public void changeStatus(TransactionStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public void softDelete(Instant now) {
        this.deletedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public boolean isTransfer() {
        return this.type == TransactionType.TRANSFER_OUT || this.type == TransactionType.TRANSFER_IN;
    }

    public boolean isExchangeOrAllowance() {
        return this.type == TransactionType.EXCHANGE_OUT
                || this.type == TransactionType.EXCHANGE_IN
                || this.type == TransactionType.ALLOWANCE_OUT
                || this.type == TransactionType.ALLOWANCE_IN;
    }

    public boolean isInstallment() {
        return this.installmentPlanId != null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    private static String normalizeCurrency(String currencyCode) {
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    public UUID getAttributedToUserId() {
        return attributedToUserId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public TransactionType getType() {
        return type;
    }

    public Scope getScope() {
        return scope;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getInstallmentPlanId() {
        return installmentPlanId;
    }

    public Integer getInstallmentNumber() {
        return installmentNumber == null ? null : installmentNumber.intValue();
    }

    public UUID getRecurringExpenseId() {
        return recurringExpenseId;
    }

    public UUID getCurrencyExchangeId() {
        return currencyExchangeId;
    }

    public UUID getAllowanceId() {
        return allowanceId;
    }

    public UUID getReceiptAttachmentId() {
        return receiptAttachmentId;
    }

    public UUID getTransferGroupId() {
        return transferGroupId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
