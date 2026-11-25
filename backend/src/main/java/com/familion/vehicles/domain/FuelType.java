package com.familion.vehicles.domain;

public enum FuelType {
    NAFTA_SUPER,
    NAFTA_PREMIUM,
    DIESEL,
    DIESEL_PREMIUM,
    GNC,
    ELECTRICO,
    HIBRIDO;

    public boolean isValidForFuelLog() {
        return this != HIBRIDO;
    }

    public boolean isValidSecondary() {
        return this != HIBRIDO && this != ELECTRICO;
    }
}
