package com.familion.bff.aggregation.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.familion.bff.core.CoreClient;
import com.familion.bff.core.CoreClientException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanAggregationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // 2026-12-01T01:30:00Z is 2026-11-30T22:30:00-03:00 in America/Argentina/Buenos_Aires
    private static final Clock MONTH_BOUNDARY_UTC_CLOCK =
            Clock.fixed(Instant.parse("2026-12-01T01:30:00Z"), ZoneOffset.UTC);

    private CoreClient coreClient;
    private PlanAggregationService service;

    @BeforeEach
    void setUp() {
        coreClient = mock(CoreClient.class);
        service = new PlanAggregationService(coreClient, Duration.ofSeconds(2), MONTH_BOUNDARY_UTC_CLOCK);
    }

    @Test
    void buildsPathsWithExpectedQueryParams() {
        PlanQuery full = new PlanQuery("FAMILY", "2026-10");
        assertThat(PlanAggregationService.buildPositionsPath(full))
                .isEqualTo("/currency-exchanges/positions?view=FAMILY");
        assertThat(PlanAggregationService.buildInstallmentsPath(full))
                .isEqualTo("/installment-plans?status=ACTIVE&view=FAMILY");
        assertThat(PlanAggregationService.buildCommitmentPath(full))
                .isEqualTo("/installment-plans/commitment?months=6&view=FAMILY");
        assertThat(PlanAggregationService.buildRecurringPath("2026-10"))
                .isEqualTo("/recurring-expenses?month=2026-10");
        assertThat(PlanAggregationService.buildGoalsPath(full))
                .isEqualTo("/savings-goals?view=FAMILY&status=ACTIVE");

        PlanQuery empty = new PlanQuery("  ", null);
        assertThat(PlanAggregationService.buildPositionsPath(empty))
                .isEqualTo("/currency-exchanges/positions");
        assertThat(PlanAggregationService.buildInstallmentsPath(empty))
                .isEqualTo("/installment-plans?status=ACTIVE");
        assertThat(PlanAggregationService.buildCommitmentPath(empty))
                .isEqualTo("/installment-plans/commitment?months=6");
        assertThat(PlanAggregationService.buildGoalsPath(empty))
                .isEqualTo("/savings-goals?status=ACTIVE");
    }

    @Test
    void resolvesDefaultMonthUsingBuenosAiresTimezone() {
        assertThat(service.resolveMonth(new PlanQuery("PERSONAL", null))).isEqualTo("2026-11");
        assertThat(service.resolveMonth(new PlanQuery("PERSONAL", "   "))).isEqualTo("2026-11");
        assertThat(service.resolveMonth(null)).isEqualTo("2026-11");
        assertThat(service.resolveMonth(new PlanQuery("PERSONAL", " 2026-08 "))).isEqualTo("2026-08");
    }

    @Test
    void aggregatesAllSectionsUntransformed() throws Exception {
        PlanQuery query = new PlanQuery("FAMILY", "2026-11");
        JsonNode positions = OBJECT_MAPPER.readTree("[{\"currencyCode\":\"USD\",\"balance\":\"500.0000\"}]");
        JsonNode installments = OBJECT_MAPPER.readTree("[{\"id\":\"inst-1\",\"status\":\"ACTIVE\"}]");
        JsonNode commitment = OBJECT_MAPPER.readTree("[{\"month\":\"2026-11\",\"total\":\"45000.0000\"}]");
        JsonNode recurring = OBJECT_MAPPER.readTree("[{\"id\":\"rec-1\",\"name\":\"Internet\"}]");
        JsonNode goals = OBJECT_MAPPER.readTree("[{\"id\":\"goal-1\",\"status\":\"ACTIVE\"}]");
        JsonNode allowances = OBJECT_MAPPER.readTree("[{\"id\":\"all-1\",\"amount\":\"10000.0000\"}]");

        when(coreClient.get("/currency-exchanges/positions?view=FAMILY", "tok-1", JsonNode.class))
                .thenReturn(positions);
        when(coreClient.get("/installment-plans?status=ACTIVE&view=FAMILY", "tok-1", JsonNode.class))
                .thenReturn(installments);
        when(coreClient.get("/installment-plans/commitment?months=6&view=FAMILY", "tok-1", JsonNode.class))
                .thenReturn(commitment);
        when(coreClient.get("/recurring-expenses?month=2026-11", "tok-1", JsonNode.class))
                .thenReturn(recurring);
        when(coreClient.get("/savings-goals?view=FAMILY&status=ACTIVE", "tok-1", JsonNode.class))
                .thenReturn(goals);
        when(coreClient.get("/family/allowances", "tok-1", JsonNode.class))
                .thenReturn(allowances);

        PlanAggregationResult result = service.aggregate("tok-1", query);

        assertThat(result).isInstanceOf(PlanAggregationResult.Ok.class);
        PlanPayload payload = ((PlanAggregationResult.Ok) result).payload();
        assertThat(payload.positions()).isEqualTo(positions);
        assertThat(payload.installments()).isEqualTo(installments);
        assertThat(payload.commitment()).isEqualTo(commitment);
        assertThat(payload.recurring()).isEqualTo(recurring);
        assertThat(payload.goals()).isEqualTo(goals);
        assertThat(payload.allowances()).isEqualTo(allowances);
    }

    @Test
    void degradesNon401ErrorsToNullWhilePreservingOtherFields() throws Exception {
        PlanQuery query = new PlanQuery("PERSONAL", null);
        JsonNode positions = OBJECT_MAPPER.readTree("[{\"currencyCode\":\"USD\"}]");
        JsonNode installments = OBJECT_MAPPER.readTree("[{\"id\":\"inst-1\"}]");
        JsonNode recurring = OBJECT_MAPPER.readTree("[{\"id\":\"rec-1\"}]");
        JsonNode goals = OBJECT_MAPPER.readTree("[{\"id\":\"goal-1\"}]");

        when(coreClient.get("/currency-exchanges/positions?view=PERSONAL", "tok-2", JsonNode.class))
                .thenReturn(positions);
        when(coreClient.get("/installment-plans?status=ACTIVE&view=PERSONAL", "tok-2", JsonNode.class))
                .thenReturn(installments);
        when(coreClient.get("/installment-plans/commitment?months=6&view=PERSONAL", "tok-2", JsonNode.class))
                .thenThrow(new CoreClientException(500, "{\"title\":\"Internal Error\"}"));
        when(coreClient.get("/recurring-expenses?month=2026-11", "tok-2", JsonNode.class))
                .thenReturn(recurring);
        when(coreClient.get("/savings-goals?view=PERSONAL&status=ACTIVE", "tok-2", JsonNode.class))
                .thenReturn(goals);
        when(coreClient.get("/family/allowances", "tok-2", JsonNode.class))
                .thenThrow(new CoreClientException(404, "{\"title\":\"Not Found\"}"));

        PlanAggregationResult result = service.aggregate("tok-2", query);

        assertThat(result).isInstanceOf(PlanAggregationResult.Ok.class);
        PlanPayload payload = ((PlanAggregationResult.Ok) result).payload();
        assertThat(payload.positions()).isEqualTo(positions);
        assertThat(payload.installments()).isEqualTo(installments);
        assertThat(payload.commitment()).isNull();
        assertThat(payload.recurring()).isEqualTo(recurring);
        assertThat(payload.goals()).isEqualTo(goals);
        assertThat(payload.allowances()).isNull();
    }

    @Test
    void returnsUnauthorizedWhenAnyCallReturns401() throws Exception {
        PlanQuery query = new PlanQuery("FAMILY", "2026-11");
        when(coreClient.get(anyString(), eq("expired"), eq(JsonNode.class)))
                .thenReturn(OBJECT_MAPPER.readTree("[]"));
        when(coreClient.get("/family/allowances", "expired", JsonNode.class))
                .thenThrow(new CoreClientException(401, "{\"title\":\"Unauthorized\"}"));

        PlanAggregationResult result = service.aggregate("expired", query);

        assertThat(result).isInstanceOf(PlanAggregationResult.Unauthorized.class);
    }
}
