package com.familion.vehicles.domain;

import com.familion.common.persistence.CreatedAtEntity;
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
@Table(name = "fuel_logs")
public class FuelLog extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "transaction_id", nullable = false, unique = true, updatable = false)
    private UUID transactionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "fuel_date", nullable = false)
    private LocalDate fuelDate;

    @Column(name = "odometer_km", nullable = false)
    private int odometerKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 25)
    private FuelType fuelType;

    @Column(name = "liters", nullable = false, precision = 12, scale = 3)
    private BigDecimal liters;

    @Column(name = "price_per_liter", nullable = false, precision = 12, scale = 4)
    private BigDecimal pricePerLiter;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "full_tank", nullable = false)
    private boolean fullTank;

    @Column(name = "station_name", length = 100)
    private String stationName;

    @Column(name = "notes", length = 255)
    private String notes;

    protected FuelLog() {}

    public FuelLog(
            UUID familyId,
            UUID vehicleId,
            UUID transactionId,
            UUID userId,
            LocalDate fuelDate,
            int odometerKm,
            FuelType fuelType,
            BigDecimal liters,
            BigDecimal pricePerLiter,
            BigDecimal totalAmount,
            String currencyCode,
            boolean fullTank,
            String stationName,
            String notes) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.fuelDate = Objects.requireNonNull(fuelDate, "fuelDate must not be null");
        this.odometerKm = odometerKm;
        this.fuelType = Objects.requireNonNull(fuelType, "fuelType must not be null");
        this.liters = Objects.requireNonNull(liters, "liters must not be null");
        this.pricePerLiter = Objects.requireNonNull(pricePerLiter, "pricePerLiter must not be null");
        this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        this.fullTank = fullTank;
        this.stationName = stationName;
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

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getFuelDate() {
        return fuelDate;
    }

    public void setFuelDate(LocalDate fuelDate) {
        this.fuelDate = fuelDate;
    }

    public int getOdometerKm() {
        return odometerKm;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    public BigDecimal getLiters() {
        return liters;
    }

    public void setLiters(BigDecimal liters) {
        this.liters = liters;
    }

    public BigDecimal getPricePerLiter() {
        return pricePerLiter;
    }

    public void setPricePerLiter(BigDecimal pricePerLiter) {
        this.pricePerLiter = pricePerLiter;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public boolean isFullTank() {
        return fullTank;
    }

    public String getStationName() {
        return stationName;
    }

    public String getNotes() {
        return notes;
    }
}
