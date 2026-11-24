package com.familion.bff.aggregation.vehicles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.familion.bff.core.CoreClient;
import com.familion.bff.core.CoreClientException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VehicleDashboardAggregationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final UUID VEHICLE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private CoreClient coreClient;
    private VehicleDashboardAggregationService service;

    @BeforeEach
    void setUp() {
        coreClient = mock(CoreClient.class);
        service = new VehicleDashboardAggregationService(coreClient, Duration.ofSeconds(2));
    }

    @Test
    void buildsPathsWithOnlyPresentQueryParams() {
        VehicleDashboardQuery full = new VehicleDashboardQuery("CONSTANT_BASE", "2026-01-01", "2026-11-24");
        assertThat(VehicleDashboardAggregationService.buildVehiclePath(VEHICLE_ID))
                .isEqualTo("/vehicles/11111111-2222-3333-4444-555555555555");
        assertThat(VehicleDashboardAggregationService.buildSummaryPath(VEHICLE_ID, full))
                .isEqualTo("/vehicles/11111111-2222-3333-4444-555555555555/summary"
                        + "?lens=CONSTANT_BASE&from=2026-01-01&to=2026-11-24");
        assertThat(VehicleDashboardAggregationService.buildFuelLogsPath(VEHICLE_ID))
                .isEqualTo("/vehicles/11111111-2222-3333-4444-555555555555/fuel-logs?size=5");
        assertThat(VehicleDashboardAggregationService.buildMaintenanceLogsPath(VEHICLE_ID))
                .isEqualTo("/vehicles/11111111-2222-3333-4444-555555555555/maintenance-logs?size=5");

        VehicleDashboardQuery empty = new VehicleDashboardQuery("  ", null, "");
        assertThat(VehicleDashboardAggregationService.buildSummaryPath(VEHICLE_ID, empty))
                .isEqualTo("/vehicles/11111111-2222-3333-4444-555555555555/summary");
    }

    @Test
    void aggregatesAllSectionsAndDerivesAlertsFromVehicle() throws Exception {
        VehicleDashboardQuery query = new VehicleDashboardQuery("NOMINAL", "2026-01-01", "2026-11-24");
        JsonNode vehicle = OBJECT_MAPPER.readTree("""
                {"id":"11111111-2222-3333-4444-555555555555","name":"Corolla","alerts":[{"type":"VTV","severity":"WARNING"}]}
                """);
        JsonNode summary = OBJECT_MAPPER.readTree("""
                {"totalCost":"125000.0000","avgConsumption":"8.2000"}
                """);
        JsonNode fuelLogsPage = OBJECT_MAPPER.readTree("""
                {"content":[{"id":"fuel-1","liters":"40.0000"}],"totalElements":1}
                """);
        JsonNode maintenancePage = OBJECT_MAPPER.readTree("""
                {"content":[{"id":"maint-1","type":"SERVICE"}],"totalElements":1}
                """);

        when(coreClient.get("/vehicles/" + VEHICLE_ID, "tok-1", JsonNode.class)).thenReturn(vehicle);
        when(coreClient.get(
                "/vehicles/" + VEHICLE_ID + "/summary?lens=NOMINAL&from=2026-01-01&to=2026-11-24",
                "tok-1",
                JsonNode.class)).thenReturn(summary);
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/fuel-logs?size=5", "tok-1", JsonNode.class))
                .thenReturn(fuelLogsPage);
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/maintenance-logs?size=5", "tok-1", JsonNode.class))
                .thenReturn(maintenancePage);

        VehicleDashboardResult result = service.aggregate("tok-1", VEHICLE_ID, query);

        assertThat(result).isInstanceOf(VehicleDashboardResult.Ok.class);
        VehicleDashboardPayload payload = ((VehicleDashboardResult.Ok) result).payload();
        assertThat(payload.vehicle()).isEqualTo(vehicle);
        assertThat(payload.summary()).isEqualTo(summary);
        assertThat(payload.recentFuelLogs()).isEqualTo(fuelLogsPage.get("content"));
        assertThat(payload.recentMaintenance()).isEqualTo(maintenancePage.get("content"));
        assertThat(payload.alerts()).isEqualTo(vehicle.get("alerts"));
    }

    @Test
    void returnsNotFoundWhenVehicleCallReturns404() throws Exception {
        VehicleDashboardQuery query = new VehicleDashboardQuery(null, null, null);
        when(coreClient.get("/vehicles/" + VEHICLE_ID, "tok-2", JsonNode.class))
                .thenThrow(new CoreClientException(404, "{\"title\":\"Not Found\",\"status\":404}"));
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/summary", "tok-2", JsonNode.class))
                .thenThrow(new CoreClientException(404, "{\"title\":\"Not Found\",\"status\":404}"));
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/fuel-logs?size=5", "tok-2", JsonNode.class))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":[]}"));
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/maintenance-logs?size=5", "tok-2", JsonNode.class))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":[]}"));

        VehicleDashboardResult result = service.aggregate("tok-2", VEHICLE_ID, query);

        assertThat(result).isInstanceOf(VehicleDashboardResult.NotFound.class);
    }

    @Test
    void degradesVehicle500ToNullVehicleAndNullAlertsWhilePreservingOtherSections() throws Exception {
        VehicleDashboardQuery query = new VehicleDashboardQuery(null, null, null);
        JsonNode summary = OBJECT_MAPPER.readTree("{\"totalCost\":\"50000.0000\"}");
        JsonNode fuelLogsPage = OBJECT_MAPPER.readTree("{\"content\":[{\"id\":\"fuel-1\"}]}");
        JsonNode maintenancePage = OBJECT_MAPPER.readTree("{\"content\":[{\"id\":\"maint-1\"}]}");

        when(coreClient.get("/vehicles/" + VEHICLE_ID, "tok-3", JsonNode.class))
                .thenThrow(new CoreClientException(500, "{\"title\":\"Internal Error\"}"));
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/summary", "tok-3", JsonNode.class))
                .thenReturn(summary);
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/fuel-logs?size=5", "tok-3", JsonNode.class))
                .thenReturn(fuelLogsPage);
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/maintenance-logs?size=5", "tok-3", JsonNode.class))
                .thenReturn(maintenancePage);

        VehicleDashboardResult result = service.aggregate("tok-3", VEHICLE_ID, query);

        assertThat(result).isInstanceOf(VehicleDashboardResult.Ok.class);
        VehicleDashboardPayload payload = ((VehicleDashboardResult.Ok) result).payload();
        assertThat(payload.vehicle()).isNull();
        assertThat(payload.alerts()).isNull();
        assertThat(payload.summary()).isEqualTo(summary);
        assertThat(payload.recentFuelLogs()).isEqualTo(fuelLogsPage.get("content"));
        assertThat(payload.recentMaintenance()).isEqualTo(maintenancePage.get("content"));
    }

    @Test
    void handlesMalformedPagesAndMissingOrNonArrayAlertsGracefully() throws Exception {
        JsonNode vehicleNoAlerts = OBJECT_MAPPER.readTree("{\"id\":\"11111111-2222-3333-4444-555555555555\"}");
        when(coreClient.get("/vehicles/" + VEHICLE_ID, "tok-4", JsonNode.class))
                .thenReturn(vehicleNoAlerts);
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/summary", "tok-4", JsonNode.class))
                .thenThrow(new CoreClientException(500, "{\"title\":\"Error\"}"));
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/fuel-logs?size=5", "tok-4", JsonNode.class))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":\"not-an-array\"}"));
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/maintenance-logs?size=5", "tok-4", JsonNode.class))
                .thenReturn(OBJECT_MAPPER.readTree("{}"));

        VehicleDashboardResult result = service.aggregate("tok-4", VEHICLE_ID, null);

        assertThat(result).isInstanceOf(VehicleDashboardResult.Ok.class);
        VehicleDashboardPayload payload = ((VehicleDashboardResult.Ok) result).payload();
        assertThat(payload.vehicle()).isEqualTo(vehicleNoAlerts);
        assertThat(payload.summary()).isNull();
        assertThat(payload.recentFuelLogs()).isNull();
        assertThat(payload.recentMaintenance()).isNull();
        assertThat(payload.alerts()).isNull();

        assertThat(VehicleDashboardAggregationService.extractAlerts(null)).isNull();
        assertThat(VehicleDashboardAggregationService.extractAlerts(OBJECT_MAPPER.readTree("{\"alerts\":null}")))
                .isNull();
        assertThat(VehicleDashboardAggregationService.extractAlerts(OBJECT_MAPPER.readTree("{\"alerts\":\"bad\"}")))
                .isNull();
        assertThat(VehicleDashboardAggregationService.extractAlerts(OBJECT_MAPPER.readTree("{\"alerts\":[]}")))
                .isEmpty();
    }

    @Test
    void returnsUnauthorizedWhenAnyCallReturns401EvenIfVehicleReturns404() throws Exception {
        when(coreClient.get(anyString(), eq("expired"), eq(JsonNode.class)))
                .thenReturn(OBJECT_MAPPER.readTree("{}"));
        when(coreClient.get("/vehicles/" + VEHICLE_ID, "expired", JsonNode.class))
                .thenThrow(new CoreClientException(404, "{\"title\":\"Not Found\"}"));
        when(coreClient.get("/vehicles/" + VEHICLE_ID + "/summary", "expired", JsonNode.class))
                .thenThrow(new CoreClientException(401, "{\"title\":\"Unauthorized\"}"));

        VehicleDashboardResult result = service.aggregate("expired", VEHICLE_ID, null);

        assertThat(result).isInstanceOf(VehicleDashboardResult.Unauthorized.class);
    }
}
