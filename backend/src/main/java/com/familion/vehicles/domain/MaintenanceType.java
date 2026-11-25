package com.familion.vehicles.domain;

public enum MaintenanceType {
    SERVICE("Service"),
    OIL_CHANGE("Cambio de aceite"),
    FILTERS("Filtros"),
    TIRES("Cubiertas"),
    BRAKES("Frenos"),
    TIMING_BELT("Correa de distribución"),
    BATTERY("Batería"),
    SUSPENSION("Suspensión"),
    ALIGNMENT("Alineación y balanceo"),
    VTV("VTV"),
    REPAIR("Reparación"),
    OTHER("Otro");

    private final String label;

    MaintenanceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
