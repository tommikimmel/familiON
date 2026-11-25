package com.familion.bff.aggregation.vehicles;

import com.fasterxml.jackson.databind.JsonNode;

public record VehicleDashboardPayload(
        JsonNode vehicle,
        JsonNode summary,
        JsonNode recentFuelLogs,
        JsonNode recentMaintenance,
        JsonNode alerts) {
}
