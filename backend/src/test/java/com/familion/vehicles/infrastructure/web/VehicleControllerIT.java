package com.familion.vehicles.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.familion.vehicles.application.api.VehicleAlertQueryApi;
import com.familion.vehicles.application.api.VehicleAlertRef;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class VehicleControllerIT extends IntegrationTestBase {

    @Autowired
    private VehicleAlertQueryApi vehicleAlertQueryApi;

    private UUID familyId;
    private UUID adultUserId;
    private UUID childUserId;
    private String adultBearer;
    private String childBearer;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        familyId = fixtures.createFamily("Familia IT Autos " + suffix);
        adultUserId = fixtures.createUser(
                familyId, "adulto." + suffix + "@familion.local", Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childUserId = fixtures.createUser(
                familyId, "hijo." + suffix + "@familion.local", Role.HIJO, FamilyVisibility.OWN_ONLY);

        adultBearer = tokens.bearer(adultUserId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        childBearer = tokens.bearer(childUserId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);
    }

    @Test
    @DisplayName("HIJO cannot create vehicle (403), ADULTO can (201)")
    void childForbiddenAdultCanCreate() {
        Map<String, Object> body = Map.of(
                "nickname", "Gol Trend",
                "brand", "Volkswagen",
                "model", "Gol",
                "modelYear", 2020,
                "licensePlate", "AA111BB",
                "primaryFuelType", "NAFTA_SUPER",
                "currentOdometerKm", 45000);

        ResponseEntity<JsonNode> childResp = client()
                .post()
                .uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> adultResp = client()
                .post()
                .uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(adultResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(adultResp.getBody().get("id")).isNotNull();
        assertThat(adultResp.getBody().get("licensePlate").asText()).isEqualTo("AA111BB");
        assertThat(adultResp.getBody().get("isActive").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Duplicate license plate in same family returns 409 Conflict")
    void duplicateLicensePlateConflict() {
        Map<String, Object> body1 = Map.of(
                "nickname", "Auto 1",
                "brand", "Ford",
                "model", "Ka",
                "modelYear", 2018,
                "licensePlate", "AB222CD",
                "primaryFuelType", "NAFTA_SUPER");

        ResponseEntity<JsonNode> resp1 = client()
                .post()
                .uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body1)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> body2 = Map.of(
                "nickname", "Auto 2",
                "brand", "Ford",
                "model", "Fiesta",
                "modelYear", 2019,
                "licensePlate", "ab222cd",
                "primaryFuelType", "NAFTA_SUPER");

        ResponseEntity<JsonNode> resp2 = client()
                .post()
                .uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body2)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("CRUD lifecycle: list, get, patch, delete")
    void crudLifecycle() {
        LocalDate vtvDue = LocalDate.now().plusDays(15);
        Map<String, Object> createBody = Map.of(
                "nickname", "Cronos",
                "brand", "Fiat",
                "model", "Cronos",
                "modelYear", 2023,
                "licensePlate", "AF333GH",
                "primaryFuelType", "NAFTA_SUPER",
                "currentOdometerKm", 15000,
                "vtvExpirationDate", vtvDue.toString());

        ResponseEntity<JsonNode> createResp = client()
                .post()
                .uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBody)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String vehicleId = createResp.getBody().get("id").asText();

        // Get by id
        ResponseEntity<JsonNode> getResp = client()
                .get()
                .uri("/api/v1/vehicles/" + vehicleId)
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("nickname").asText()).isEqualTo("Cronos");
        assertThat(getResp.getBody().get("alerts").isArray()).isTrue();
        assertThat(getResp.getBody().get("alerts")).hasSize(1);
        assertThat(getResp.getBody().get("alerts").get(0).get("severity").asText()).isEqualTo("WARNING");

        // List
        ResponseEntity<JsonNode> listResp = client()
                .get()
                .uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody().isArray()).isTrue();
        assertThat(listResp.getBody()).isNotEmpty();

        // Patch
        Map<String, Object> patchBody = Map.of(
                "nickname", "Cronos Black",
                "currentOdometerKm", 18000);
        ResponseEntity<JsonNode> patchResp = client()
                .patch()
                .uri("/api/v1/vehicles/" + vehicleId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(patchBody)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResp.getBody().get("nickname").asText()).isEqualTo("Cronos Black");
        assertThat(patchResp.getBody().get("currentOdometerKm").asInt()).isEqualTo(18000);

        // Delete
        ResponseEntity<Void> deleteResp = client()
                .delete()
                .uri("/api/v1/vehicles/" + vehicleId)
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toBodilessEntity();
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Vehicle no longer in active list
        ResponseEntity<JsonNode> listAfterResp = client()
                .get()
                .uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        boolean foundInList = false;
        for (JsonNode n : listAfterResp.getBody()) {
            if (n.get("id").asText().equals(vehicleId)) {
                foundInList = true;
                break;
            }
        }
        assertThat(foundInList).isFalse();
    }

    @Test
    @DisplayName("activeAlerts query returns active WARNING and OVERDUE alerts")
    void activeAlertsApi() {
        LocalDate overdueVtv = LocalDate.now().minusDays(5);
        Map<String, Object> createBody = Map.of(
                "nickname", "Tracker",
                "brand", "Chevrolet",
                "model", "Tracker",
                "modelYear", 2021,
                "licensePlate", "AG777ZZ",
                "primaryFuelType", "NAFTA_PREMIUM",
                "vtvExpirationDate", overdueVtv.toString());

        ResponseEntity<JsonNode> createResp = client()
                .post()
                .uri("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBody)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<VehicleAlertRef> activeAlerts = vehicleAlertQueryApi.activeAlerts();
        assertThat(activeAlerts).anyMatch(
                a -> a.vehicleNickname().equals("Tracker") && a.severity().equals("OVERDUE"));
    }
}
