package com.familion.bff.aggregation.home;

import com.familion.bff.aggregation.ParallelCalls;
import com.familion.bff.aggregation.PartialResult;
import com.familion.bff.core.CoreClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class HomeAggregationService {

    private static final Logger log = LoggerFactory.getLogger(HomeAggregationService.class);
    private static final Duration AGGREGATION_TIMEOUT = Duration.ofSeconds(3);
    private static final int RECENT_TRANSACTIONS_SIZE = 10;
    private static final String RECENT_TRANSACTIONS_SORT = "transactionDate,desc";
    private static final int HTTP_OK = 200;

    private final CoreClient coreClient;
    private final Duration timeout;

    @Autowired
    public HomeAggregationService(CoreClient coreClient) {
        this(coreClient, AGGREGATION_TIMEOUT);
    }

    HomeAggregationService(CoreClient coreClient, Duration timeout) {
        this.coreClient = Objects.requireNonNull(coreClient, "coreClient must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    public HomeAggregationResult aggregate(String accessToken, HomeQuery query) {
        HomeQuery effectiveQuery = query != null ? query : new HomeQuery(null, null, null, null);

        String summaryPath = buildAnalyticsPath("/analytics/summary", effectiveQuery);
        String byCategoryPath = buildAnalyticsPath("/analytics/by-category", effectiveQuery);
        String byPersonPath = effectiveQuery.isFamilyView()
                ? buildAnalyticsPath("/analytics/by-person", effectiveQuery)
                : null;
        String recentTransactionsPath = buildRecentTransactionsPath(effectiveQuery);

        PartialResult<JsonNode> summaryCall = ParallelCalls.call(
                () -> coreClient.get(summaryPath, accessToken, JsonNode.class));
        PartialResult<JsonNode> byCategoryCall = ParallelCalls.call(
                () -> coreClient.get(byCategoryPath, accessToken, JsonNode.class));
        PartialResult<JsonNode> byPersonCall = byPersonPath != null
                ? ParallelCalls.call(() -> coreClient.get(byPersonPath, accessToken, JsonNode.class))
                : null;
        PartialResult<JsonNode> recentTransactionsCall = ParallelCalls.call(
                () -> extractTransactionsContent(
                        coreClient.get(recentTransactionsPath, accessToken, JsonNode.class)));
        PartialResult<JsonNode> latestRatesCall = ParallelCalls.call(
                () -> coreClient.get("/economics/indices/latest", accessToken, JsonNode.class));
        PartialResult<Long> unreadNotificationsCall = ParallelCalls.call(
                () -> extractUnreadCount(
                        coreClient.get("/notifications/unread-count", accessToken, JsonNode.class)));

        ParallelCalls.awaitAll(
                timeout,
                summaryCall,
                byCategoryCall,
                byPersonCall,
                recentTransactionsCall,
                latestRatesCall,
                unreadNotificationsCall);

        if (anyUnauthorized(
                summaryCall,
                byCategoryCall,
                byPersonCall,
                recentTransactionsCall,
                latestRatesCall,
                unreadNotificationsCall)) {
            return new HomeAggregationResult.Unauthorized();
        }

        logIfDegraded("summary", summaryCall);
        logIfDegraded("byCategory", byCategoryCall);
        logIfDegraded("byPerson", byPersonCall);
        logIfDegraded("recentTransactions", recentTransactionsCall);
        logIfDegraded("latestRates", latestRatesCall);
        logIfDegraded("unreadNotifications", unreadNotificationsCall);

        HomePayload payload = new HomePayload(
                summaryCall.valueOrNull(),
                byCategoryCall.valueOrNull(),
                byPersonCall != null ? byPersonCall.valueOrNull() : null,
                recentTransactionsCall.valueOrNull(),
                latestRatesCall.valueOrNull(),
                unreadNotificationsCall.valueOrNull());

        return new HomeAggregationResult.Ok(payload);
    }

    static String buildAnalyticsPath(String basePath, HomeQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(basePath);
        addIfHasText(builder, "view", query.view());
        addIfHasText(builder, "lens", query.lens());
        addIfHasText(builder, "granularity", query.granularity());
        addIfHasText(builder, "date", query.date());
        return builder.build().encode().toUriString();
    }

    static String buildRecentTransactionsPath(HomeQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/transactions");
        addIfHasText(builder, "view", query.view());
        addIfHasText(builder, "lens", query.lens());
        builder.queryParam("size", RECENT_TRANSACTIONS_SIZE);
        builder.queryParam("sort", RECENT_TRANSACTIONS_SORT);
        return builder.build().encode().toUriString();
    }

    private static void addIfHasText(UriComponentsBuilder builder, String name, String value) {
        if (StringUtils.hasText(value)) {
            builder.queryParam(name, value);
        }
    }

    private static JsonNode extractTransactionsContent(JsonNode page) {
        if (page == null) {
            return null;
        }
        JsonNode content = page.get("content");
        if (content == null || content.isNull() || !content.isArray()) {
            return null;
        }
        return content;
    }

    private static Long extractUnreadCount(JsonNode node) {
        if (node == null) {
            return null;
        }
        JsonNode count = node.get("count");
        if (count == null || count.isNull() || !count.isIntegralNumber()) {
            return null;
        }
        return count.longValue();
    }

    private static boolean anyUnauthorized(PartialResult<?>... results) {
        for (PartialResult<?> result : results) {
            if (result != null && result.unauthorized()) {
                return true;
            }
        }
        return false;
    }

    private static void logIfDegraded(String section, PartialResult<?> result) {
        if (result == null) {
            return;
        }
        Optional<Integer> status = result.status();
        if (status.isEmpty()) {
            log.debug("Home aggregation section '{}' degraded to null (timeout or error)", section);
        } else if (status.get() != HTTP_OK) {
            log.debug("Home aggregation section '{}' degraded to null (status={})", section, status.get());
        }
    }
}
