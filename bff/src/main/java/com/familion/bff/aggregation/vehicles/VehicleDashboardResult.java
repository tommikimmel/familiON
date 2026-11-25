package com.familion.bff.aggregation.vehicles;

public sealed interface VehicleDashboardResult {

    record Ok(VehicleDashboardPayload payload) implements VehicleDashboardResult {
    }

    record NotFound() implements VehicleDashboardResult {
    }

    record Unauthorized() implements VehicleDashboardResult {
    }
}
