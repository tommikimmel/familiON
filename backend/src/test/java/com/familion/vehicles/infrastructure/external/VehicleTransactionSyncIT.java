package com.familion.vehicles.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
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
class VehicleTransactionSyncIT extends IntegrationTestBase {

    private UUID familyId;
    private UUID adultUserId;
    private UUID accountId;
    private String adultBearer;
    private UUID vehicleId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        familyId = fixtures.createFamily("Familia Sync " + suffix);
        adultUserId = fixtures.createUser(
                familyId, "adulto." + suffix + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        accountId = fixtures.createAccount(familyId, adultUserId, "BANK", "ARS");

        adultBearer = tokens.bearer(adultUserId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        Map<String, Object> vehicleBody = Map.of(
                "nickname", "Vento",
                "brand", "Volkswagen",
                "model", "Vento",
                "modelYear", 2020,
                "licensePlate", "AH" + suffix.substring(0, 4) + "JK",
                "primaryFuelType", "NAFTA_PREMIUM",
                "currentOdometerKm", 40000);

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
    @DisplayName("Editing transaction via PATCH /transactions/{id} updates fuel log amount, date, and price per liter")
    void patchTransactionUpdatesFuelLog() {
        Map<String, Object> fuelBody = Map.of(
                "fuelDate", "2026-05-15",
                "odometerKm", 40500,
                "fuelType", "NAFTA_PREMIUM",
                "liters", 50.0,
                "pricePerLiter", 1000.0,
                "currencyCode", "ARS",
                "accountId", accountId.toString());

        ResponseEntity<JsonNode> fuelResp = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fuelBody)
                .retrieve()
                .toEntity(JsonNode.class);

        String fuelLogId = fuelResp.getBody().get("id").asText();
        String txId = fuelResp.getBody().get("transactionId").asText();

        // PATCH transaction amount to 60000 and date to 2026-05-20
        Map<String, Object> patchTx = Map.of(
                "amount", 60000.0,
                "transactionDate", "2026-05-20");

        ResponseEntity<JsonNode> patchResp = client()
                .patch()
                .uri("/api/v1/transactions/" + txId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(patchTx)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Fetch fuel logs to verify updated values
        ResponseEntity<JsonNode> listResp = client()
                .get()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        JsonNode logNode = null;
        for (JsonNode n : listResp.getBody().get("content")) {
            if (n.get("id").asText().equals(fuelLogId)) {
                logNode = n;
                break;
            }
        }
        assertThat(logNode).isNotNull();
        assertThat(logNode.get("totalAmount").asDouble()).isEqualTo(60000.0);
        assertThat(logNode.get("fuelDate").asText()).isEqualTo("2026-05-20");
        // price_per_liter = 60000 / 50 = 1200
        assertThat(logNode.get("pricePerLiter").asDouble()).isEqualTo(1200.0);
    }

    @Test
    @DisplayName("Deleting transaction via DELETE /transactions/{id} deletes the linked fuel log")
    void deleteTransactionDeletesFuelLog() {
        Map<String, Object> fuelBody = Map.of(
                "fuelDate", "2026-05-15",
                "odometerKm", 40500,
                "fuelType", "NAFTA_PREMIUM",
                "liters", 50.0,
                "pricePerLiter", 1000.0,
                "currencyCode", "ARS",
                "accountId", accountId.toString());

        ResponseEntity<JsonNode> fuelResp = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fuelBody)
                .retrieve()
                .toEntity(JsonNode.class);

        String fuelLogId = fuelResp.getBody().get("id").asText();
        String txId = fuelResp.getBody().get("transactionId").asText();

        // DELETE transaction
        ResponseEntity<Void> delResp = client()
                .delete()
                .uri("/api/v1/transactions/" + txId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toBodilessEntity();
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Fetch fuel logs: fuel log must no longer exist
        ResponseEntity<JsonNode> listResp = client()
                .get()
                .uri("/api/v1/vehicles/" + vehicleId + "/fuel-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        boolean found = false;
        for (JsonNode n : listResp.getBody().get("content")) {
            if (n.get("id").asText().equals(fuelLogId)) {
                found = true;
                break;
            }
        }
        assertThat(found).isFalse();
    }

    @Test
    @DisplayName("Deleting transaction of maintenance log sets transaction_id=NULL and cost_amount=0")
    void deleteTransactionUpdatesMaintenanceLog() {
        Map<String, Object> maintBody = Map.of(
                "maintenanceType", "TIRES",
                "odometerKm", 41000,
                "description", "Cambio de cubiertas",
                "costAmount", 120000.0,
                "currencyCode", "ARS",
                "accountId", accountId.toString());

        ResponseEntity<JsonNode> maintResp = client()
                .post()
                .uri("/api/v1/vehicles/" + vehicleId + "/maintenance-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(maintBody)
                .retrieve()
                .toEntity(JsonNode.class);

        String maintLogId = maintResp.getBody().get("id").asText();
        String txId = maintResp.getBody().get("transactionId").asText();

        // DELETE transaction
        ResponseEntity<Void> delResp = client()
                .delete()
                .uri("/api/v1/transactions/" + txId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toBodilessEntity();
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Fetch maintenance logs: log still exists with cost=0 and transactionId=null
        ResponseEntity<JsonNode> listResp = client()
                .get()
                .uri("/api/v1/vehicles/" + vehicleId + "/maintenance-logs")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);

        JsonNode logNode = null;
        for (JsonNode n : listResp.getBody().get("content")) {
            if (n.get("id").asText().equals(maintLogId)) {
                logNode = n;
                break;
            }
        }
        assertThat(logNode).isNotNull();
        assertThat(logNode.get("costAmount").asDouble()).isEqualTo(0.0);
        assertThat(logNode.get("transactionId").isNull()).isTrue();
    }
}
