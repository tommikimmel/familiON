package com.familion.vehicles.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
        properties = {
            "familion.economics.sync-on-startup=false",
            "ECON_SYNC_CRON=-",
            "spring.datasource.hikari.maximum-pool-size=3"
        })
class VehicleSummaryIT extends IntegrationTestBase {

    private UUID familyId;
    private UUID adultUserId;
    private UUID accountId;
    private String adultBearer;
    private UUID vehicleId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        familyId = fixtures.createFamily("Familia Summary " + suffix);
        adultUserId = fixtures.createUser(
                familyId, "adulto." + suffix + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        accountId = fixtures.createAccount(familyId, adultUserId, "BANK", "ARS");

        adultBearer = tokens.bearer(adultUserId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        Map<String, Object> vehicleBody = Map.of(
                "nickname", "Cruze",
                "brand", "Chevrolet",
                "model", "Cruze",
                "modelYear", 2022,
                "licensePlate", "AG" + suffix.substring(0, 4) + "XY",
                "primaryFuelType", "NAFTA_SUPER",
                "currentOdometerKm", 30000);

        ResponseEntity<JsonNode> vResp = client()
                .post()
                .uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(vehicleBody)
                .retrieve()
                .toEntity(JsonNode.class);

        vehicleId = UUID.fromString(vResp.getBody().get("id").asText());
    }

    @Test
    @DisplayName("Summary with REFERENCE_CURRENCY and NOMINAL lenses")
    void summaryWithLenses() {
        LocalDate today = LocalDate.now();

        // 1st fuel log (full tank)
        Map<String, Object> fuel1 = Map.of(
                "fuelDate", today.minusDays(20).toString(),
                "odometerKm", 30500,
                "fuelType", "NAFTA_SUPER",
                "liters", 40.0,
                "pricePerLiter", 1000.0,
                "currencyCode", "ARS",
                "fullTank", true,
                "accountId", accountId.toString());
        client().post().uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fuel1).retrieve().toBodilessEntity();

        // 2nd fuel log (full tank)
        Map<String, Object> fuel2 = Map.of(
                "fuelDate", today.minusDays(5).toString(),
                "odometerKm", 31100,
                "fuelType", "NAFTA_SUPER",
                "liters", 35.0,
                "pricePerLiter", 1000.0,
                "currencyCode", "ARS",
                "fullTank", true,
                "accountId", accountId.toString());
        client().post().uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fuel2).retrieve().toBodilessEntity();

        // Maintenance log
        Map<String, Object> maint = Map.of(
                "maintenanceType", "OIL_CHANGE",
                "maintenanceDate", today.minusDays(10).toString(),
                "odometerKm", 30800,
                "description", "Cambio de aceite",
                "costAmount", 75000.0,
                "currencyCode", "ARS",
                "accountId", accountId.toString());
        client().post().uri("/api/v1/vehicles/" + vehicleId + "/maintenance-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(maint).retrieve().toBodilessEntity();

        // Query summary with NOMINAL lens
        ResponseEntity<JsonNode> nominalResp = client()
                .get()
                .uri("/api/v1/vehicles/" + vehicleId + "/summary?lens=NOMINAL")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(nominalResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode nomBody = nominalResp.getBody();
        assertThat(nomBody.get("lens").asText()).isEqualTo("NOMINAL");
        assertThat(nomBody.get("unit").asText()).isEqualTo("ARS");
        assertThat(nomBody.get("distanceKm").asInt()).isEqualTo(600); // 31100 - 30500
        assertThat(nomBody.get("totalLiters").asDouble()).isEqualTo(75.0);
        assertThat(nomBody.get("fuelCost").asDouble()).isEqualTo(75000.0); // 40000 + 35000
        assertThat(nomBody.get("maintenanceCost").asDouble()).isEqualTo(75000.0);
        assertThat(nomBody.get("totalCost").asDouble()).isEqualTo(150000.0);
        // Consumption between fuel1 and fuel2: 600 km, 35 liters -> 600/35 = 17.1429 km/L
        assertThat(nomBody.get("avgKmPerLiter").asDouble()).isBetween(17.14, 17.15);
        assertThat(nomBody.get("costPerKm").asDouble()).isEqualTo(250.0); // 150000 / 600
        assertThat(nomBody.get("monthly").isArray()).isTrue();
        assertThat(nomBody.get("pricePerLiterTrend").isArray()).isTrue();
        assertThat(nomBody.get("pricePerLiterTrend")).hasSize(2);

        // Query summary with REFERENCE_CURRENCY lens
        ResponseEntity<JsonNode> refResp = client()
                .get()
                .uri("/api/v1/vehicles/" + vehicleId + "/summary?lens=REFERENCE_CURRENCY")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(refResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode refBody = refResp.getBody();
        assertThat(refBody.get("lens").asText()).isEqualTo("REFERENCE_CURRENCY");
        assertThat(refBody.get("unit").asText()).isEqualTo("USD");
        assertThat(refBody.get("totalCost").asDouble()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("INCOME_PCT lens returns 400 Bad Request")
    void incomePctLensReturns400() {
        ResponseEntity<JsonNode> resp = client()
                .get()
                .uri("/api/v1/vehicles/" + vehicleId + "/summary?lens=INCOME_PCT")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
