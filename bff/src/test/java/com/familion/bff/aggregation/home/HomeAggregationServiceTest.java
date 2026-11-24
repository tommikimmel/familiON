package com.familion.bff.aggregation.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.bff.core.CoreClient;
import com.familion.bff.core.CoreClientException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeAggregationServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CoreClient coreClient;
    private HomeAggregationService service;

    @BeforeEach
    void setUp() {
        coreClient = mock(CoreClient.class);
        service = new HomeAggregationService(coreClient, Duration.ofSeconds(2));
    }

    @Test
    void buildsAnalyticsAndTransactionsPathsWithOnlyPresentParams() {
        HomeQuery full = new HomeQuery("FAMILY", "CONSTANT_BASE", "MONTH", "2026-11-23");
        assertThat(HomeAggregationService.buildAnalyticsPath("/analytics/summary", full))
                .isEqualTo("/analytics/summary?view=FAMILY&lens=CONSTANT_BASE&granularity=MONTH&date=2026-11-23");
        assertThat(HomeAggregationService.buildRecentTransactionsPath(full))
                .isEqualTo("/transactions?view=FAMILY&lens=CONSTANT_BASE&size=10&sort=transactionDate,desc");

        HomeQuery empty = new HomeQuery(null, "  ", null, "");
        assertThat(HomeAggregationService.buildAnalyticsPath("/analytics/summary", empty))
                .isEqualTo("/analytics/summary");
        assertThat(HomeAggregationService.buildRecentTransactionsPath(empty))
                .isEqualTo("/transactions?size=10&sort=transactionDate,desc");
    }

    @Test
    void aggregatesAllSectionsForFamilyView() throws Exception {
        HomeQuery query = new HomeQuery("FAMILY", "NOMINAL", "MONTH", "2026-11-23");
        JsonNode summary = OBJECT_MAPPER.readTree("{\"income\":\"5000.0000\"}");
        JsonNode byCategory = OBJECT_MAPPER.readTree("{\"total\":\"2000.0000\",\"slices\":[]}");
        JsonNode byPerson = OBJECT_MAPPER.readTree("{\"total\":\"2000.0000\",\"slices\":[]}");
        JsonNode transactionsPage = OBJECT_MAPPER.readTree("{\"content\":[{\"id\":\"tx-1\"}],\"totalElements\":1}");
        JsonNode latestRates = OBJECT_MAPPER.readTree("[{\"indexCode\":\"USD_BLUE\",\"value\":\"1200.000000\"}]");
        JsonNode unreadCount = OBJECT_MAPPER.readTree("{\"count\":4}");

        when(coreClient.get(
                "/analytics/summary?view=FAMILY&lens=NOMINAL&granularity=MONTH&date=2026-11-23",
                "tok-1",
                JsonNode.class)).thenReturn(summary);
        when(coreClient.get(
                "/analytics/by-category?view=FAMILY&lens=NOMINAL&granularity=MONTH&date=2026-11-23",
                "tok-1",
                JsonNode.class)).thenReturn(byCategory);
        when(coreClient.get(
                "/analytics/by-person?view=FAMILY&lens=NOMINAL&granularity=MONTH&date=2026-11-23",
                "tok-1",
                JsonNode.class)).thenReturn(byPerson);
        when(coreClient.get(
                "/transactions?view=FAMILY&lens=NOMINAL&size=10&sort=transactionDate,desc",
                "tok-1",
                JsonNode.class)).thenReturn(transactionsPage);
        when(coreClient.get("/economics/indices/latest", "tok-1", JsonNode.class)).thenReturn(latestRates);
        when(coreClient.get("/notifications/unread-count", "tok-1", JsonNode.class)).thenReturn(unreadCount);

        HomeAggregationResult result = service.aggregate("tok-1", query);

        assertThat(result).isInstanceOf(HomeAggregationResult.Ok.class);
        HomePayload payload = ((HomeAggregationResult.Ok) result).payload();
        assertThat(payload.summary()).isEqualTo(summary);
        assertThat(payload.byCategory()).isEqualTo(byCategory);
        assertThat(payload.byPerson()).isEqualTo(byPerson);
        assertThat(payload.recentTransactions()).isEqualTo(transactionsPage.get("content"));
        assertThat(payload.latestRates()).isEqualTo(latestRates);
        assertThat(payload.unreadNotifications()).isEqualTo(4L);
    }

    @Test
    void skipsByPersonCallWhenViewIsNotFamily() throws Exception {
        HomeQuery query = new HomeQuery("PERSONAL", "NOMINAL", "MONTH", "2026-11-23");
        when(coreClient.get(anyString(), eq("tok-2"), eq(JsonNode.class)))
                .thenReturn(OBJECT_MAPPER.readTree("{}"));
        when(coreClient.get(
                "/transactions?view=PERSONAL&lens=NOMINAL&size=10&sort=transactionDate,desc",
                "tok-2",
                JsonNode.class))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":[]}"));
        when(coreClient.get("/notifications/unread-count", "tok-2", JsonNode.class))
                .thenReturn(OBJECT_MAPPER.readTree("{\"count\":0}"));

        HomeAggregationResult result = service.aggregate("tok-2", query);

        assertThat(result).isInstanceOf(HomeAggregationResult.Ok.class);
        HomePayload payload = ((HomeAggregationResult.Ok) result).payload();
        assertThat(payload.byPerson()).isNull();
        assertThat(payload.recentTransactions().isArray()).isTrue();
        assertThat(payload.recentTransactions()).isEmpty();
        assertThat(payload.unreadNotifications()).isEqualTo(0L);
        verify(coreClient, never()).get(
                eq("/analytics/by-person?view=PERSONAL&lens=NOMINAL&granularity=MONTH&date=2026-11-23"),
                anyString(),
                eq(JsonNode.class));
    }

    @Test
    void handlesMalformedTransactionsPageAndUnreadCountGracefully() throws Exception {
        HomeQuery query = new HomeQuery("PERSONAL", null, null, null);
        when(coreClient.get("/analytics/summary?view=PERSONAL", "tok-3", JsonNode.class))
                .thenReturn(OBJECT_MAPPER.readTree("{\"income\":\"0\"}"));
        when(coreClient.get("/analytics/by-category?view=PERSONAL", "tok-3", JsonNode.class))
                .thenThrow(new CoreClientException(500, "{\"title\":\"Error\"}"));
        when(coreClient.get("/transactions?view=PERSONAL&size=10&sort=transactionDate,desc", "tok-3", JsonNode.class))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":\"not-an-array\"}"));
        when(coreClient.get("/economics/indices/latest", "tok-3", JsonNode.class))
                .thenReturn(null);
        when(coreClient.get("/notifications/unread-count", "tok-3", JsonNode.class))
                .thenReturn(OBJECT_MAPPER.readTree("{\"count\":\"abc\"}"));

        HomeAggregationResult result = service.aggregate("tok-3", query);

        assertThat(result).isInstanceOf(HomeAggregationResult.Ok.class);
        HomePayload payload = ((HomeAggregationResult.Ok) result).payload();
        assertThat(payload.summary()).isNotNull();
        assertThat(payload.byCategory()).isNull();
        assertThat(payload.byPerson()).isNull();
        assertThat(payload.recentTransactions()).isNull();
        assertThat(payload.latestRates()).isNull();
        assertThat(payload.unreadNotifications()).isNull();
    }

    @Test
    void returnsUnauthorizedWhenAnyCallReturns401() throws Exception {
        HomeQuery query = new HomeQuery("FAMILY", null, null, null);
        when(coreClient.get(anyString(), eq("expired"), eq(JsonNode.class)))
                .thenReturn(OBJECT_MAPPER.readTree("{}"));
        when(coreClient.get("/economics/indices/latest", "expired", JsonNode.class))
                .thenThrow(new CoreClientException(401, "{\"title\":\"Unauthorized\"}"));

        HomeAggregationResult result = service.aggregate("expired", query);

        assertThat(result).isInstanceOf(HomeAggregationResult.Unauthorized.class);
    }
}
