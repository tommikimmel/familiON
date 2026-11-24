package com.familion.finance.goals.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.common.security.FamilyVisibility;
import com.familion.common.security.Role;
import com.familion.common.test.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

class SavingsGoalControllerIT extends IntegrationTestBase {

    private static final ZoneId AR_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanEconomicState() {
        jdbcTemplate.update("DELETE FROM economic_indices");
        Set<String> econKeys = redisTemplate.keys("econ:*");
        if (econKeys != null && !econKeys.isEmpty()) {
            redisTemplate.delete(econKeys);
        }
    }

    private RestClient patchCapableClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void createVisibilityAndScopeRulesEnforceRolesAndChildVisibility() {
        UUID familyId = fixtures.createFamily("Familia Metas Vis " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-goal-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childSummaryId = fixtures.createUser(
                familyId,
                "child-sum-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.FAMILY_SUMMARY);
        UUID childOwnId = fixtures.createUser(
                familyId,
                "child-own-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.OWN_ONLY);

        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childSummaryBearer =
                tokens.bearer(childSummaryId, familyId, Role.HIJO, FamilyVisibility.FAMILY_SUMMARY);
        String childOwnBearer =
                tokens.bearer(childOwnId, familyId, Role.HIJO, FamilyVisibility.OWN_ONLY);

        // HIJO creating FAMILY goal -> 403
        ResponseEntity<JsonNode> childFamilyCreate = client()
                .post()
                .uri("/api/v1/savings-goals")
                .header(HttpHeaders.AUTHORIZATION, childSummaryBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Viaje Familiar",
                        "scope", "FAMILY",
                        "targetAmount", new BigDecimal("3000"),
                        "targetUnit", "USD"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childFamilyCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ADULTO creates FAMILY goal -> 201 with owner=null
        ResponseEntity<JsonNode> familyGoalResp = client()
                .post()
                .uri("/api/v1/savings-goals")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Viaje Familiar",
                        "scope", "FAMILY",
                        "targetAmount", new BigDecimal("3000"),
                        "targetUnit", "USD"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(familyGoalResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(familyGoalResp.getBody().path("owner").isNull()).isTrue();
        assertThat(familyGoalResp.getBody().path("status").asText()).isEqualTo("ACTIVE");

        // ADULTO creates PERSONAL goal
        ResponseEntity<JsonNode> adultPersonalResp = client()
                .post()
                .uri("/api/v1/savings-goals")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Notebook Adulto",
                        "scope", "PERSONAL",
                        "targetAmount", new BigDecimal("1500"),
                        "targetUnit", "USD"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(adultPersonalResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(adultPersonalResp.getBody().path("owner").path("id").asText())
                .isEqualTo(adultId.toString());

        // HIJO creates PERSONAL goal -> 201 with owner = HIJO
        ResponseEntity<JsonNode> childPersonalResp = client()
                .post()
                .uri("/api/v1/savings-goals")
                .header(HttpHeaders.AUTHORIZATION, childSummaryBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Bicicleta Hijo",
                        "scope", "PERSONAL",
                        "targetAmount", new BigDecimal("500"),
                        "targetUnit", "USD"))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(childPersonalResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(childPersonalResp.getBody().path("owner").path("id").asText())
                .isEqualTo(childSummaryId.toString());

        // PERSONAL view with empty query params -> only caller's personal goals
        ResponseEntity<JsonNode> adultPersonalList = client()
                .get()
                .uri("/api/v1/savings-goals?view=&status=")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(adultPersonalList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extractNames(adultPersonalList.getBody())).containsExactly("Notebook Adulto");

        ResponseEntity<JsonNode> childPersonalList = client()
                .get()
                .uri("/api/v1/savings-goals?view=PERSONAL")
                .header(HttpHeaders.AUTHORIZATION, childSummaryBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(extractNames(childPersonalList.getBody())).containsExactly("Bicicleta Hijo");

        // FAMILY view as ADULTO -> family goals + everyone's personal goals
        ResponseEntity<JsonNode> adultFamilyList = client()
                .get()
                .uri("/api/v1/savings-goals?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(extractNames(adultFamilyList.getBody()))
                .containsExactlyInAnyOrder("Viaje Familiar", "Notebook Adulto", "Bicicleta Hijo");

        // FAMILY view as HIJO with FAMILY_SUMMARY -> sees FAMILY goals only
        ResponseEntity<JsonNode> childSummaryFamilyList = client()
                .get()
                .uri("/api/v1/savings-goals?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, childSummaryBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(childSummaryFamilyList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extractNames(childSummaryFamilyList.getBody())).containsExactly("Viaje Familiar");

        // FAMILY view as HIJO with OWN_ONLY -> 403
        ResponseEntity<JsonNode> childOwnFamilyList = client()
                .get()
                .uri("/api/v1/savings-goals?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, childOwnBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childOwnFamilyList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        UUID familyGoalId = UUID.fromString(familyGoalResp.getBody().path("id").asText());
        // HIJO OWN_ONLY cannot read or contribute to a FAMILY goal -> 403
        ResponseEntity<JsonNode> childOwnContribList = client()
                .get()
                .uri("/api/v1/savings-goals/" + familyGoalId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, childOwnBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childOwnContribList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> childOwnContribAdd = client()
                .post()
                .uri("/api/v1/savings-goals/" + familyGoalId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, childOwnBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", new BigDecimal("10"), "currencyCode", "USD"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childOwnContribAdd.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Non-owner HIJO cannot PATCH FAMILY goal -> 403
        ResponseEntity<JsonNode> childPatchFamily = patchCapableClient()
                .patch()
                .uri("/api/v1/savings-goals/" + familyGoalId)
                .header(HttpHeaders.AUTHORIZATION, childSummaryBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "No autorizado"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(childPatchFamily.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void contributionsConvertProgressAutoCompleteAndDeleteRecalculates() {
        LocalDate today = LocalDate.now(AR_ZONE);
        LocalDate contribDay1 = today.minusDays(2);
        LocalDate contribDay2 = today.minusDays(1);
        upsertIndex("USD_MEP", contribDay1, new BigDecimal("1000.000000"));
        upsertIndex("USD_MEP", contribDay2, new BigDecimal("1250.000000"));
        upsertIndex("USD_MEP", today, new BigDecimal("1200.000000"));

        UUID familyId = fixtures.createFamily("Familia Metas Contrib " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-contrib-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID childId = fixtures.createUser(
                familyId,
                "child-contrib-" + UUID.randomUUID() + "@familion.test",
                Role.HIJO,
                FamilyVisibility.FAMILY_FULL);
        UUID accountId = fixtures.createAccount(familyId, adultId, "BANK", "ARS");
        UUID validTxId = insertTransaction(familyId, adultId, accountId);

        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String childBearer =
                tokens.bearer(childId, familyId, Role.HIJO, FamilyVisibility.FAMILY_FULL);

        LocalDate targetDate = today.plusMonths(4);
        ResponseEntity<JsonNode> created = client()
                .post()
                .uri("/api/v1/savings-goals")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Fondo Emergencia",
                        "scope", "FAMILY",
                        "targetAmount", new BigDecimal("1000"),
                        "targetUnit", "USD",
                        "targetDate", targetDate.toString()))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID goalId = UUID.fromString(created.getBody().path("id").asText());

        // Tiny contribution converting to 0.0000 USD -> 422
        ResponseEntity<JsonNode> tinyContrib = client()
                .post()
                .uri("/api/v1/savings-goals/" + goalId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "amount", new BigDecimal("0.01"),
                        "currencyCode", "ARS",
                        "contributionDate", contribDay1.toString()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(tinyContrib.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // First contribution: 600,000 ARS at USD_MEP = 1000 -> 600.0000 USD, linked to validTxId
        ResponseEntity<JsonNode> c1 = client()
                .post()
                .uri("/api/v1/savings-goals/" + goalId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "amount", new BigDecimal("600000"),
                        "currencyCode", "ARS",
                        "contributionDate", contribDay1.toString(),
                        "notes", "Primer aporte",
                        "transactionId", validTxId.toString()))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(c1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(c1.getBody().path("amountInGoalUnit").asText()).isEqualTo("600.0000");
        assertThat(c1.getBody().path("transactionId").asText()).isEqualTo(validTxId.toString());
        assertThat(c1.getBody().path("user").path("id").asText()).isEqualTo(adultId.toString());
        UUID c1Id = UUID.fromString(c1.getBody().path("id").asText());

        // Verify no extra transaction was created
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE family_id = ?", Integer.class, familyId);
        assertThat(txCount).isEqualTo(1);

        // Verify progress after first contribution
        JsonNode goalAfterC1 = fetchGoalFromFamilyList(adultBearer, goalId);
        assertThat(goalAfterC1.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(goalAfterC1.path("accumulatedInUnit").asText()).isEqualTo("600.0000");
        assertThat(goalAfterC1.path("progressPct").asText()).isEqualTo("60.0000");
        assertThat(goalAfterC1.path("remainingInUnit").asText()).isEqualTo("400.0000");
        assertThat(goalAfterC1.path("monthsLeft").asInt()).isEqualTo(4);
        assertThat(goalAfterC1.path("suggestedMonthlyInUnit").asText()).isEqualTo("100.0000");
        assertThat(goalAfterC1.path("contributionsCount").asInt()).isEqualTo(1);
        assertThat(goalAfterC1.path("lastContributionDate").asText()).isEqualTo(contribDay1.toString());
        // 600 USD * 1200 ARS/USD today = 720000.0000
        assertThat(goalAfterC1.path("accumulatedInBaseToday").asText()).isEqualTo("720000.0000");

        // Second contribution by HIJO: 500,000 ARS at USD_MEP = 1250 -> 400.0000 USD -> reaches 100% -> COMPLETED
        ResponseEntity<JsonNode> c2 = client()
                .post()
                .uri("/api/v1/savings-goals/" + goalId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "amount", new BigDecimal("500000"),
                        "currencyCode", "ARS",
                        "contributionDate", contribDay2.toString()))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(c2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(c2.getBody().path("amountInGoalUnit").asText()).isEqualTo("400.0000");
        UUID c2Id = UUID.fromString(c2.getBody().path("id").asText());

        // Goal is now auto-COMPLETED and filterable by status=COMPLETED
        ResponseEntity<JsonNode> completedList = client()
                .get()
                .uri("/api/v1/savings-goals?view=FAMILY&status=COMPLETED")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(completedList.getBody()).hasSize(1);
        JsonNode goalCompleted = completedList.getBody().get(0);
        assertThat(goalCompleted.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(goalCompleted.path("accumulatedInUnit").asText()).isEqualTo("1000.0000");
        assertThat(goalCompleted.path("progressPct").asText()).isEqualTo("100.0000");
        assertThat(goalCompleted.path("remainingInUnit").asText()).isEqualTo("0.0000");
        assertThat(goalCompleted.path("contributionsCount").asInt()).isEqualTo(2);
        assertThat(goalCompleted.path("lastContributionDate").asText())
                .isEqualTo(contribDay2.toString());

        // List contributions returns newest first
        ResponseEntity<JsonNode> contribList = client()
                .get()
                .uri("/api/v1/savings-goals/" + goalId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(contribList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contribList.getBody()).hasSize(2);
        assertThat(contribList.getBody().get(0).path("id").asText()).isEqualTo(c2Id.toString());
        assertThat(contribList.getBody().get(1).path("id").asText()).isEqualTo(c1Id.toString());

        // HIJO cannot delete ADULTO's contribution -> 403
        ResponseEntity<JsonNode> forbiddenDelete = client()
                .delete()
                .uri("/api/v1/savings-goals/" + goalId + "/contributions/" + c1Id)
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(forbiddenDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // HIJO deletes own contribution -> 204, goal recalculates and reverts to ACTIVE
        ResponseEntity<Void> deleted = client()
                .delete()
                .uri("/api/v1/savings-goals/" + goalId + "/contributions/" + c2Id)
                .header(HttpHeaders.AUTHORIZATION, childBearer)
                .retrieve()
                .toBodilessEntity();
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode goalAfterDelete = fetchGoalFromFamilyList(adultBearer, goalId);
        assertThat(goalAfterDelete.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(goalAfterDelete.path("accumulatedInUnit").asText()).isEqualTo("600.0000");
        assertThat(goalAfterDelete.path("progressPct").asText()).isEqualTo("60.0000");
        assertThat(goalAfterDelete.path("remainingInUnit").asText()).isEqualTo("400.0000");
        assertThat(goalAfterDelete.path("contributionsCount").asInt()).isEqualTo(1);
    }

    @Test
    void uvaAndArsIpcGoalsConvertAndValueInBaseToday() {
        LocalDate today = LocalDate.now(AR_ZONE);
        LocalDate twoMonthsAgo = today.minusMonths(2);
        LocalDate oneMonthAgo = today.minusMonths(1);
        upsertIndex("UVA", oneMonthAgo, new BigDecimal("1000.000000"));
        upsertIndex("UVA", today, new BigDecimal("1200.000000"));
        upsertIndex("IPC_ACUMULADO", twoMonthsAgo, new BigDecimal("100.000000"));
        upsertIndex("IPC_ACUMULADO", oneMonthAgo, new BigDecimal("125.000000"));
        upsertIndex("IPC_ACUMULADO", today, new BigDecimal("150.000000"));

        UUID familyId = fixtures.createFamily("Familia Metas Indices " + UUID.randomUUID());
        UUID adultId = fixtures.createUser(
                familyId,
                "adult-idx-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        String adultBearer =
                tokens.bearer(adultId, familyId, Role.ADULTO, FamilyVisibility.FAMILY_FULL);

        // UVA goal
        ResponseEntity<JsonNode> uvaGoalResp = client()
                .post()
                .uri("/api/v1/savings-goals")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Terreno UVA",
                        "scope", "FAMILY",
                        "targetAmount", new BigDecimal("500"),
                        "targetUnit", "UVA"))
                .retrieve()
                .toEntity(JsonNode.class);
        UUID uvaGoalId = UUID.fromString(uvaGoalResp.getBody().path("id").asText());

        // Contribute 100,000 ARS when UVA = 1000 -> 100.0000 UVA
        ResponseEntity<JsonNode> uvaContrib = client()
                .post()
                .uri("/api/v1/savings-goals/" + uvaGoalId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "amount", new BigDecimal("100000"),
                        "currencyCode", "ARS",
                        "contributionDate", oneMonthAgo.toString()))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(uvaContrib.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(uvaContrib.getBody().path("amountInGoalUnit").asText()).isEqualTo("100.0000");

        // Today UVA = 1200 -> 100 UVA * 1200 = 120000.0000 ARS
        JsonNode uvaGoal = fetchGoalFromFamilyList(adultBearer, uvaGoalId);
        assertThat(uvaGoal.path("accumulatedInUnit").asText()).isEqualTo("100.0000");
        assertThat(uvaGoal.path("accumulatedInBaseToday").asText()).isEqualTo("120000.0000");

        // ARS_IPC goal backdated to twoMonthsAgo so IPC(createdAt) = 100
        ResponseEntity<JsonNode> ipcGoalResp = client()
                .post()
                .uri("/api/v1/savings-goals")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Reserva Real",
                        "scope", "FAMILY",
                        "targetAmount", new BigDecimal("100000"),
                        "targetUnit", "ARS_IPC"))
                .retrieve()
                .toEntity(JsonNode.class);
        UUID ipcGoalId = UUID.fromString(ipcGoalResp.getBody().path("id").asText());
        jdbcTemplate.update(
                "UPDATE savings_goals SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(twoMonthsAgo.atStartOfDay(AR_ZONE).toInstant()),
                ipcGoalId);

        // Contribute 25,000 ARS on oneMonthAgo (IPC=125) -> 25000 * 100 / 125 = 20000.0000
        ResponseEntity<JsonNode> ipcContrib = client()
                .post()
                .uri("/api/v1/savings-goals/" + ipcGoalId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, adultBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "amount", new BigDecimal("25000"),
                        "currencyCode", "ARS",
                        "contributionDate", oneMonthAgo.toString()))
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(ipcContrib.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ipcContrib.getBody().path("amountInGoalUnit").asText()).isEqualTo("20000.0000");

        // Today IPC = 150 -> accumulatedInBaseToday = 20000 * 150 / 100 = 30000.0000
        JsonNode ipcGoal = fetchGoalFromFamilyList(adultBearer, ipcGoalId);
        assertThat(ipcGoal.path("accumulatedInUnit").asText()).isEqualTo("20000.0000");
        assertThat(ipcGoal.path("accumulatedInBaseToday").asText()).isEqualTo("30000.0000");
    }

    @Test
    void patchAndFamilyIsolationProtectDataAcrossFamilies() {
        UUID familyA = fixtures.createFamily("Familia A " + UUID.randomUUID());
        UUID familyB = fixtures.createFamily("Familia B " + UUID.randomUUID());
        UUID adultA = fixtures.createUser(
                familyA,
                "adult-a-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID adultB = fixtures.createUser(
                familyB,
                "adult-b-" + UUID.randomUUID() + "@familion.test",
                Role.ADULTO,
                FamilyVisibility.FAMILY_FULL);
        UUID accountB = fixtures.createAccount(familyB, adultB, "BANK", "ARS");
        UUID txFamilyB = insertTransaction(familyB, adultB, accountB);

        String bearerA = tokens.bearer(adultA, familyA, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        String bearerB = tokens.bearer(adultB, familyB, Role.ADULTO, FamilyVisibility.FAMILY_FULL);
        RestClient patchClient = patchCapableClient();

        ResponseEntity<JsonNode> created = client()
                .post()
                .uri("/api/v1/savings-goals")
                .header(HttpHeaders.AUTHORIZATION, bearerA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", "Meta A",
                        "scope", "FAMILY",
                        "targetAmount", new BigDecimal("1000"),
                        "targetUnit", "USD",
                        "targetDate", LocalDate.now(AR_ZONE).plusMonths(6).toString()))
                .retrieve()
                .toEntity(JsonNode.class);
        UUID goalAId = UUID.fromString(created.getBody().path("id").asText());

        // Cross-family transactionId on contribution -> 404
        ResponseEntity<JsonNode> foreignTxResp = client()
                .post()
                .uri("/api/v1/savings-goals/" + goalAId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, bearerA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "amount", new BigDecimal("100"),
                        "currencyCode", "USD",
                        "transactionId", txFamilyB.toString()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(foreignTxResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Add valid contribution in Family A
        ResponseEntity<JsonNode> contribA = client()
                .post()
                .uri("/api/v1/savings-goals/" + goalAId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, bearerA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", new BigDecimal("100"), "currencyCode", "USD"))
                .retrieve()
                .toEntity(JsonNode.class);
        UUID contribAId = UUID.fromString(contribA.getBody().path("id").asText());

        // PATCH by Family A clears targetDate and updates name/status
        Map<String, Object> patchBody = new HashMap<>();
        patchBody.put("name", "Meta A Actualizada");
        patchBody.put("targetAmount", new BigDecimal("2000"));
        patchBody.put("targetDate", null);
        patchBody.put("status", "PAUSED");
        ResponseEntity<JsonNode> patched = patchClient
                .patch()
                .uri("/api/v1/savings-goals/" + goalAId)
                .header(HttpHeaders.AUTHORIZATION, bearerA)
                .contentType(MediaType.APPLICATION_JSON)
                .body(patchBody)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody().path("name").asText()).isEqualTo("Meta A Actualizada");
        assertThat(patched.getBody().path("targetAmount").asText()).isEqualTo("2000.0000");
        assertThat(patched.getBody().path("targetDate").isNull()).isTrue();
        assertThat(patched.getBody().path("monthsLeft").isNull()).isTrue();
        assertThat(patched.getBody().path("suggestedMonthlyInUnit").isNull()).isTrue();
        assertThat(patched.getBody().path("status").asText()).isEqualTo("PAUSED");

        // Family B sees empty list and gets 404 on every operation against Family A's goal
        ResponseEntity<JsonNode> listB = client()
                .get()
                .uri("/api/v1/savings-goals?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, bearerB)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(listB.getBody()).isEmpty();

        ResponseEntity<JsonNode> patchB = patchClient
                .patch()
                .uri("/api/v1/savings-goals/" + goalAId)
                .header(HttpHeaders.AUTHORIZATION, bearerB)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Hack"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(patchB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> listContribB = client()
                .get()
                .uri("/api/v1/savings-goals/" + goalAId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, bearerB)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(listContribB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> addContribB = client()
                .post()
                .uri("/api/v1/savings-goals/" + goalAId + "/contributions")
                .header(HttpHeaders.AUTHORIZATION, bearerB)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", new BigDecimal("50"), "currencyCode", "USD"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(addContribB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> delContribB = client()
                .delete()
                .uri("/api/v1/savings-goals/" + goalAId + "/contributions/" + contribAId)
                .header(HttpHeaders.AUTHORIZATION, bearerB)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {})
                .toEntity(JsonNode.class);
        assertThat(delContribB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private JsonNode fetchGoalFromFamilyList(String bearer, UUID goalId) {
        ResponseEntity<JsonNode> resp = client()
                .get()
                .uri("/api/v1/savings-goals?view=FAMILY")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .retrieve()
                .toEntity(JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (JsonNode node : resp.getBody()) {
            if (goalId.toString().equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("Savings goal not found: " + goalId);
    }

    private void upsertIndex(String indexCode, LocalDate effectiveDate, BigDecimal value) {
        jdbcTemplate.update(
                """
                INSERT INTO economic_indices (id, index_code, effective_date, value, source)
                VALUES (?, ?, ?, ?, 'MANUAL')
                ON CONFLICT (index_code, effective_date)
                DO UPDATE SET value = EXCLUDED.value, source = EXCLUDED.source
                """,
                UUID.randomUUID(),
                indexCode,
                effectiveDate,
                value);
    }

    private UUID insertTransaction(UUID familyId, UUID userId, UUID accountId) {
        UUID txId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id, family_id, user_id, attributed_to_user_id, account_id,
                    type, scope, status, amount, currency_code, transaction_date
                )
                VALUES (?, ?, ?, ?, ?, 'EXPENSE', 'FAMILY', 'POSTED', 600000.0000, 'ARS', ?)
                """,
                txId,
                familyId,
                userId,
                userId,
                accountId,
                LocalDate.now(AR_ZONE));
        return txId;
    }

    private static List<String> extractNames(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(node -> names.add(node.path("name").asText()));
        return names;
    }
}
