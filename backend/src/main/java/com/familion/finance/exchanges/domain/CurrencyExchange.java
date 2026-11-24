package com.familion.finance.exchanges.domain;

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
@Table(name = "currency_exchanges")
public class CurrencyExchange extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "target_account_id", nullable = false, updatable = false)
    private UUID targetAccountId;

    @Column(name = "source_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal sourceAmount;

    @Column(name = "source_currency", nullable = false, length = 10, updatable = false)
    private String sourceCurrency;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal targetAmount;

    @Column(name = "target_currency", nullable = false, length = 10, updatable = false)
    private String targetCurrency;

    @Column(
            name = "applied_exchange_rate",
            nullable = false,
            precision = 19,
            scale = 6,
            updatable = false)
    private BigDecimal appliedExchangeRate;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal feeAmount;

    @Column(name = "fee_currency", length = 10, updatable = false)
    private String feeCurrency;

    @Column(name = "exchange_date", nullable = false, updatable = false)
    private LocalDate exchangeDate;

    @Column(name = "notes", length = 255, updatable = false)
    private String notes;

    protected CurrencyExchange() {}

    private CurrencyExchange(
            UUID familyId,
            UUID userId,
            UUID sourceAccountId,
            UUID targetAccountId,
            BigDecimal sourceAmount,
            String sourceCurrency,
            BigDecimal targetAmount,
            String targetCurrency,
            BigDecimal appliedExchangeRate,
            BigDecimal feeAmount,
            String feeCurrency,
            LocalDate exchangeDate,
            String notes,
            Instant createdAt) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.sourceAccountId =
                Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        this.targetAccountId =
                Objects.requireNonNull(targetAccountId, "targetAccountId must not be null");
        this.sourceAmount = Money.scale(sourceAmount);
        this.sourceCurrency =
                Objects.requireNonNull(sourceCurrency, "sourceCurrency must not be null")
                        .trim()
                        .toUpperCase(Locale.ROOT);
        this.targetAmount = Money.scale(targetAmount);
        this.targetCurrency =
                Objects.requireNonNull(targetCurrency, "targetCurrency must not be null")
                        .trim()
                        .toUpperCase(Locale.ROOT);
        this.appliedExchangeRate = Money.rate(appliedExchangeRate);
        this.feeAmount = Money.scale(feeAmount);
        this.feeCurrency = feeCurrency == null ? null : feeCurrency.trim().toUpperCase(Locale.ROOT);
        this.exchangeDate = Objects.requireNonNull(exchangeDate, "exchangeDate must not be null");
        this.notes = notes;
        if (createdAt != null) {
            setCreatedAt(createdAt);
        }
    }

    public static CurrencyExchange create(
            UUID familyId,
            UUID userId,
            UUID sourceAccountId,
            UUID targetAccountId,
            BigDecimal sourceAmount,
            String sourceCurrency,
            BigDecimal targetAmount,
            String targetCurrency,
            BigDecimal appliedExchangeRate,
            BigDecimal feeAmount,
            String feeCurrency,
            LocalDate exchangeDate,
            String notes,
            Instant createdAt) {
        return new CurrencyExchange(
                familyId,
                userId,
                sourceAccountId,
                targetAccountId,
                sourceAmount,
                sourceCurrency,
                targetAmount,
                targetCurrency,
                appliedExchangeRate,
                feeAmount,
                feeCurrency,
                exchangeDate,
                notes,
                createdAt);
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

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getSourceAmount() {
        return sourceAmount;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public String getTargetCurrency() {
        return targetCurrency;
    }

    public BigDecimal getAppliedExchangeRate() {
        return appliedExchangeRate;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public String getFeeCurrency() {
        return feeCurrency;
    }

    public LocalDate getExchangeDate() {
        return exchangeDate;
    }

    public String getNotes() {
        return notes;
    }
}
