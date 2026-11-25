package com.familion.bff.aggregation.vehicles;

import com.familion.bff.aggregation.ParallelCalls;
import com.familion.bff.aggregation.PartialResult;
import com.familion.bff.core.CoreClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class VehicleDashboardAggregationService {

    private static final Logger log = LoggerFactory.getLogger(VehicleDashboardAggregationService.class);
    private static final Duration AGGREGATION_TIMEOUT = Duration.ofSeconds(3);
    private static final int RECENT_LOGS_SIZE = 5;
    private static final int HTTP_OK = 200;
    private static final int HTTP_NOT_FOUND = 404;

    private final CoreClient coreClient;
    private final Duration timeout;

    @Autowired
    public VehicleDashboardAggregationService(CoreClient coreClient) {
        this(coreClient, AGGREGATION_TIMEOUT);
    }

    VehicleDashboardAggregationService(CoreClient coreClient, Duration timeout) {
        this.coreClient = Objects.requireNonNull(coreClient, "coreClient must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    public VehicleDashboardResult aggregate(
            String accessToken,
            UUID vehicleId,
            VehicleDashboardQuery query) {
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        VehicleDashboardQuery effectiveQuery = query != null ? query : new VehicleDashboardQuery(null, null, null);

        String vehiclePath = buildVehiclePath(vehicleId);
        String summaryPath = buildSummaryPath(vehicleId, effectiveQuery);
        String fuelLogsPath = buildFuelLogsPath(vehicleId);
        String maintenanceLogsPath = buildMaintenanceLogsPath(vehicleId);

        PartialResult<JsonNode> vehicleCall = ParallelCalls.call(
                () -> coreClient.get(vehiclePath, accessToken, JsonNode.class));
        PartialResult<JsonNode> summaryCall = ParallelCalls.call(
                () -> coreClient.get(summaryPath, accessToken, JsonNode.class));
        PartialResult<JsonNode> recentFuelLogsCall = ParallelCalls.call(
                () -> extractPageContent(coreClient.get(fuelLogsPath, accessToken, JsonNode.class)));
        PartialResult<JsonNode> recentMaintenanceCall = ParallelCalls.call(
                () -> extractPageContent(coreClient.get(maintenanceLogsPath, accessToken, JsonNode.class)));

        ParallelCalls.awaitAll(
                timeout,
                vehicleCall,
                summaryCall,
                recentFuelLogsCall,
                recentMaintenanceCall);

        if (anyUnauthorized(vehicleCall, summaryCall, recentFuelLogsCall, recentMaintenanceCall)) {
            return new VehicleDashboardResult.Unauthorized();
        }

        if (isNotFound(vehicleCall)) {
            return new VehicleDashboardResult.NotFound();
        }

        logIfDegraded("vehicle", vehicleCall);
        logIfDegraded("summary", summaryCall);
        logIfDegraded("recentFuelLogs", recentFuelLogsCall);
        logIfDegraded("recentMaintenance", recentMaintenanceCall);

        JsonNode vehicle = vehicleCall.valueOrNull();
        JsonNode alerts = extractAlerts(vehicle);

        VehicleDashboardPayload payload = new VehicleDashboardPayload(
                vehicle,
                summaryCall.valueOrNull(),
                recentFuelLogsCall.valueOrNull(),
                recentMaintenanceCall.valueOrNull(),
                alerts);

        return new VehicleDashboardResult.Ok(payload);
    }

    static String buildVehiclePath(UUID vehicleId) {
        return UriComponentsBuilder.fromPath("/vehicles/{id}")
                .buildAndExpand(vehicleId)
                .encode()
                .toUriString();
    }

    static String buildSummaryPath(UUID vehicleId, VehicleDashboardQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/vehicles/{id}/summary");
        addIfHasText(builder, "lens", query.lens());
        addIfHasText(builder, "from", query.from());
        addIfHasText(builder, "to", query.to());
        return builder.buildAndExpand(vehicleId).encode().toUriString();
    }

    static String buildFuelLogsPath(UUID vehicleId) {
        return UriComponentsBuilder.fromPath("/vehicles/{id}/fuel-logs")
                .queryParam("size", RECENT_LOGS_SIZE)
                .buildAndExpand(vehicleId)
                .encode()
                .toUriString();
    }

    static String buildMaintenanceLogsPath(UUID vehicleId) {
        return UriComponentsBuilder.fromPath("/vehicles/{id}/maintenance-logs")
                .queryParam("size", RECENT_LOGS_SIZE)
                .buildAndExpand(vehicleId)
                .encode()
                .toUriString();
    }

    static JsonNode extractPageContent(JsonNode page) {
        if (page == null) {
            return null;
        }
        JsonNode content = page.get("content");
        if (content == null || content.isNull() || !content.isArray()) {
            return null;
        }
        return content;
    }

    static JsonNode extractAlerts(JsonNode vehicle) {
        if (vehicle == null) {
            return null;
        }
        JsonNode alerts = vehicle.get("alerts");
        if (alerts == null || alerts.isNull() || !alerts.isArray()) {
            return null;
        }
        return alerts;
    }

    private static void addIfHasText(UriComponentsBuilder builder, String name, String value) {
        if (StringUtils.hasText(value)) {
            builder.queryParam(name, value);
        }
    }

    private static boolean anyUnauthorized(PartialResult<?>... results) {
        for (PartialResult<?> result : results) {
            if (result != null && result.unauthorized()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNotFound(PartialResult<?> result) {
        return result != null && result.status().filter(status -> status == HTTP_NOT_FOUND).isPresent();
    }

    private static void logIfDegraded(String section, PartialResult<?> result) {
        if (result == null) {
            return;
        }
        Optional<Integer> status = result.status();
        if (status.isEmpty()) {
            log.debug("Vehicle dashboard aggregation section '{}' degraded to null (timeout or error)", section);
        } else if (status.get() != HTTP_OK) {
            log.debug("Vehicle dashboard aggregation section '{}' degraded to null (status={})", section, status.get());
        }
    }
}
