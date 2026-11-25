package com.familion.vehicles.domain;

import com.familion.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "nickname", nullable = false, length = 80)
    private String nickname;

    @Column(name = "brand", nullable = false, length = 60)
    private String brand;

    @Column(name = "model", nullable = false, length = 80)
    private String model;

    @Column(name = "model_year", nullable = false)
    private short modelYear;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_fuel_type", nullable = false, length = 25)
    private FuelType primaryFuelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_fuel_type", length = 25)
    private FuelType secondaryFuelType;

    @Column(name = "current_odometer_km", nullable = false)
    private int currentOdometerKm;

    @Column(name = "vtv_expiration_date")
    private LocalDate vtvExpirationDate;

    @Column(name = "insurance_expiration")
    private LocalDate insuranceExpiration;

    @Column(name = "insurance_company", length = 100)
    private String insuranceCompany;

    @Column(name = "notes")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Vehicle() {}

    public Vehicle(
            UUID familyId,
            String nickname,
            String brand,
            String model,
            int modelYear,
            String licensePlate,
            FuelType primaryFuelType,
            FuelType secondaryFuelType,
            int currentOdometerKm,
            LocalDate vtvExpirationDate,
            LocalDate insuranceExpiration,
            String insuranceCompany,
            String notes) {
        this.familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        this.nickname = Objects.requireNonNull(nickname, "nickname must not be null");
        this.brand = Objects.requireNonNull(brand, "brand must not be null");
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.modelYear = (short) modelYear;
        this.licensePlate = Objects.requireNonNull(licensePlate, "licensePlate must not be null");
        this.primaryFuelType = Objects.requireNonNull(primaryFuelType, "primaryFuelType must not be null");
        this.secondaryFuelType = secondaryFuelType;
        this.currentOdometerKm = Math.max(0, currentOdometerKm);
        this.vtvExpirationDate = vtvExpirationDate;
        this.insuranceExpiration = insuranceExpiration;
        this.insuranceCompany = insuranceCompany;
        this.notes = notes;
        this.active = true;
    }

    public void updateOdometerIfGreater(int newOdometer) {
        if (newOdometer > this.currentOdometerKm) {
            this.currentOdometerKm = newOdometer;
        }
    }

    public void softDelete(Instant when) {
        this.active = false;
        this.deletedAt = when != null ? when : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getModelYear() {
        return modelYear;
    }

    public void setModelYear(int modelYear) {
        this.modelYear = (short) modelYear;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public FuelType getPrimaryFuelType() {
        return primaryFuelType;
    }

    public void setPrimaryFuelType(FuelType primaryFuelType) {
        this.primaryFuelType = primaryFuelType;
    }

    public FuelType getSecondaryFuelType() {
        return secondaryFuelType;
    }

    public void setSecondaryFuelType(FuelType secondaryFuelType) {
        this.secondaryFuelType = secondaryFuelType;
    }

    public int getCurrentOdometerKm() {
        return currentOdometerKm;
    }

    public void setCurrentOdometerKm(int currentOdometerKm) {
        this.currentOdometerKm = currentOdometerKm;
    }

    public LocalDate getVtvExpirationDate() {
        return vtvExpirationDate;
    }

    public void setVtvExpirationDate(LocalDate vtvExpirationDate) {
        this.vtvExpirationDate = vtvExpirationDate;
    }

    public LocalDate getInsuranceExpiration() {
        return insuranceExpiration;
    }

    public void setInsuranceExpiration(LocalDate insuranceExpiration) {
        this.insuranceExpiration = insuranceExpiration;
    }

    public String getInsuranceCompany() {
        return insuranceCompany;
    }

    public void setInsuranceCompany(String insuranceCompany) {
        this.insuranceCompany = insuranceCompany;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
