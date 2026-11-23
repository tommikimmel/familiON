package com.familion.finance.transactions.domain;

import com.familion.common.money.Money;
import com.familion.common.persistence.CreatedAtEntity;
import com.familion.economics.application.api.EconomicSnapshotData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "economic_snapshots")
public class EconomicSnapshot extends CreatedAtEntity implements Persistable<UUID> {

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "reference_currency", nullable = false, length = 10)
    private String referenceCurrency;

    @Column(name = "reference_rate_source", nullable = false, length = 30)
    private String referenceRateSource;

    @Column(name = "reference_exchange_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal referenceExchangeRate;

    @Column(name = "amount_in_reference", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountInReference;

    @Column(name = "base_currency_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseCurrencyAmount;

    @Column(name = "ipc_index_value", precision = 19, scale = 6)
    private BigDecimal ipcIndexValue;

    @Column(name = "uva_value", precision = 19, scale = 6)
    private BigDecimal uvaValue;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Transient
    private boolean isNew = true;

    protected EconomicSnapshot() {}

    private EconomicSnapshot(UUID transactionId, EconomicSnapshotData data) {
        this.transactionId =
                Objects.requireNonNull(transactionId, "transactionId must not be null");
        apply(data);
        this.isNew = true;
    }

    public static EconomicSnapshot from(UUID transactionId, EconomicSnapshotData data) {
        return new EconomicSnapshot(transactionId, data);
    }

    public void updateFrom(EconomicSnapshotData data) {
        apply(data);
    }

    private void apply(EconomicSnapshotData data) {
        Objects.requireNonNull(data, "data must not be null");
        this.referenceCurrency =
                Objects.requireNonNull(data.referenceCurrency(), "referenceCurrency must not be null");
        this.referenceRateSource = Objects.requireNonNull(
                data.referenceRateSource(), "referenceRateSource must not be null");
        this.referenceExchangeRate = Money.rate(Objects.requireNonNull(
                data.referenceExchangeRate(), "referenceExchangeRate must not be null"));
        this.amountInReference = Money.scale(
                Objects.requireNonNull(data.amountInReference(), "amountInReference must not be null"));
        this.baseCurrencyAmount = Money.scale(Objects.requireNonNull(
                data.baseCurrencyAmount(), "baseCurrencyAmount must not be null"));
        this.ipcIndexValue = data.ipcIndexValue() == null ? null : Money.rate(data.ipcIndexValue());
        this.uvaValue = data.uvaValue() == null ? null : Money.rate(data.uvaValue());
        this.snapshotDate =
                Objects.requireNonNull(data.snapshotDate(), "snapshotDate must not be null");
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return transactionId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getReferenceCurrency() {
        return referenceCurrency;
    }

    public String getReferenceRateSource() {
        return referenceRateSource;
    }

    public BigDecimal getReferenceExchangeRate() {
        return referenceExchangeRate;
    }

    public BigDecimal getAmountInReference() {
        return amountInReference;
    }

    public BigDecimal getBaseCurrencyAmount() {
        return baseCurrencyAmount;
    }

    public BigDecimal getIpcIndexValue() {
        return ipcIndexValue;
    }

    public BigDecimal getUvaValue() {
        return uvaValue;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }
}
