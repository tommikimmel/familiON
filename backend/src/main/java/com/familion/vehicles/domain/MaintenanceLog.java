package com.familion.vehicles.domain;

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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "maintenance_logs")
public class MaintenanceLog extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "transaction_id", unique = true)
    private UUID transactionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_type", nullable = false, length = 30)
    private MaintenanceType maintenanceType;

    @Column(name = "maintenance_date", nullable = false)
    private LocalDate maintenanceDate;

    @Column(name = "odometer_km", nullable = false)
    private int odometerKm;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "workshop_name", length = 120)
    private String workshopName;

    @Column(name = "cost_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal costAmount;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "next_due_km")
    private Integer nextDueKm;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "notes")
    private String notes;

    protected MaintenanceLog() {}

    public MaintenanceLog(
            UUID familyId,
            UUID vehicleId,
            UUID transactionId,
            UUID userId,
            MaintenanceType maintenanceType,
            LocalDate maintenanceDate,
            int odometerKm,
            String description,
            String workshopName,
            BigDecimal costAmount,
            String currencyCode,
            Integer nextDueKm,
            LocalDate nextDueDate,
            String notes) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        this.transactionId = transactionId;
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.maintenanceType = Objects.requireNonNull(maintenanceType, "maintenanceType must not be null");
        this.maintenanceDate = Objects.requireNonNull(maintenanceDate, "maintenanceDate must not be null");
        this.odometerKm = odometerKm;
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.workshopName = workshopName;
        this.costAmount = Money.scale(Objects.requireNonNull(costAmount, "costAmount must not be null"));
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        this.nextDueKm = nextDueKm;
        this.nextDueDate = nextDueDate;
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public MaintenanceType getMaintenanceType() {
        return maintenanceType;
    }

    public LocalDate getMaintenanceDate() {
        return maintenanceDate;
    }

    public void setMaintenanceDate(LocalDate maintenanceDate) {
        this.maintenanceDate = maintenanceDate;
    }

    public int getOdometerKm() {
        return odometerKm;
    }

    public String getDescription() {
        return description;
    }

    public String getWorkshopName() {
        return workshopName;
    }

    public BigDecimal getCostAmount() {
        return costAmount;
    }

    public void setCostAmount(BigDecimal costAmount) {
        this.costAmount = Money.scale(costAmount);
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Integer getNextDueKm() {
        return nextDueKm;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public String getNotes() {
        return notes;
    }
}
