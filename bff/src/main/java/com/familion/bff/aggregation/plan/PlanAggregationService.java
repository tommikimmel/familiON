package com.familion.bff.aggregation.plan;

import com.familion.bff.aggregation.ParallelCalls;
import com.familion.bff.aggregation.PartialResult;
import com.familion.bff.core.CoreClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PlanAggregationService {

    static final ZoneId BUENOS_AIRES_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final Logger log = LoggerFactory.getLogger(PlanAggregationService.class);
    private static final Duration AGGREGATION_TIMEOUT = Duration.ofSeconds(3);
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final int COMMITMENT_MONTHS = 6;
    private static final String ALLOWANCES_PATH = "/family/allowances";
    private static final int HTTP_OK = 200;

    private final CoreClient coreClient;
    private final Duration timeout;
    private final Clock clock;

    @Autowired
    public PlanAggregationService(CoreClient coreClient) {
        this(coreClient, AGGREGATION_TIMEOUT, Clock.system(BUENOS_AIRES_ZONE));
    }

    PlanAggregationService(CoreClient coreClient, Duration timeout, Clock clock) {
        this.coreClient = Objects.requireNonNull(coreClient, "coreClient must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public PlanAggregationResult aggregate(String accessToken, PlanQuery query) {
        PlanQuery effectiveQuery = query != null ? query : new PlanQuery(null, null);
        String effectiveMonth = resolveMonth(effectiveQuery);

        String positionsPath = buildPositionsPath(effectiveQuery);
        String installmentsPath = buildInstallmentsPath(effectiveQuery);
        String commitmentPath = buildCommitmentPath(effectiveQuery);
        String recurringPath = buildRecurringPath(effectiveMonth);
        String goalsPath = buildGoalsPath(effectiveQuery);

        PartialResult<JsonNode> positionsCall = ParallelCalls.call(
                () -> coreClient.get(positionsPath, accessToken, JsonNode.class));
        PartialResult<JsonNode> installmentsCall = ParallelCalls.call(
                () -> coreClient.get(installmentsPath, accessToken, JsonNode.class));
        PartialResult<JsonNode> commitmentCall = ParallelCalls.call(
                () -> coreClient.get(commitmentPath, accessToken, JsonNode.class));
        PartialResult<JsonNode> recurringCall = ParallelCalls.call(
                () -> coreClient.get(recurringPath, accessToken, JsonNode.class));
        PartialResult<JsonNode> goalsCall = ParallelCalls.call(
                () -> coreClient.get(goalsPath, accessToken, JsonNode.class));
        PartialResult<JsonNode> allowancesCall = ParallelCalls.call(
                () -> coreClient.get(ALLOWANCES_PATH, accessToken, JsonNode.class));

        ParallelCalls.awaitAll(
                timeout,
                positionsCall,
                installmentsCall,
                commitmentCall,
                recurringCall,
                goalsCall,
                allowancesCall);

        if (anyUnauthorized(
                positionsCall,
                installmentsCall,
                commitmentCall,
                recurringCall,
                goalsCall,
                allowancesCall)) {
            return new PlanAggregationResult.Unauthorized();
        }

        logIfDegraded("positions", positionsCall);
        logIfDegraded("installments", installmentsCall);
        logIfDegraded("commitment", commitmentCall);
        logIfDegraded("recurring", recurringCall);
        logIfDegraded("goals", goalsCall);
        logIfDegraded("allowances", allowancesCall);

        PlanPayload payload = new PlanPayload(
                positionsCall.valueOrNull(),
                installmentsCall.valueOrNull(),
                commitmentCall.valueOrNull(),
                recurringCall.valueOrNull(),
                goalsCall.valueOrNull(),
                allowancesCall.valueOrNull());

        return new PlanAggregationResult.Ok(payload);
    }

    String resolveMonth(PlanQuery query) {
        if (query != null && StringUtils.hasText(query.month())) {
            return query.month().trim();
        }
        return YearMonth.now(clock.withZone(BUENOS_AIRES_ZONE)).toString();
    }

    static String buildPositionsPath(PlanQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/currency-exchanges/positions");
        addIfHasText(builder, "view", query.view());
        return builder.build().encode().toUriString();
    }

    static String buildInstallmentsPath(PlanQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/installment-plans");
        builder.queryParam("status", ACTIVE_STATUS);
        addIfHasText(builder, "view", query.view());
        return builder.build().encode().toUriString();
    }

    static String buildCommitmentPath(PlanQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/installment-plans/commitment");
        builder.queryParam("months", COMMITMENT_MONTHS);
        addIfHasText(builder, "view", query.view());
        return builder.build().encode().toUriString();
    }

    static String buildRecurringPath(String month) {
        return UriComponentsBuilder.fromPath("/recurring-expenses")
                .queryParam("month", month)
                .build()
                .encode()
                .toUriString();
    }

    static String buildGoalsPath(PlanQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/savings-goals");
        addIfHasText(builder, "view", query.view());
        builder.queryParam("status", ACTIVE_STATUS);
        return builder.build().encode().toUriString();
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

    private static void logIfDegraded(String section, PartialResult<?> result) {
        if (result == null) {
            return;
        }
        Optional<Integer> status = result.status();
        if (status.isEmpty()) {
            log.debug("Plan aggregation section '{}' degraded to null (timeout or error)", section);
        } else if (status.get() != HTTP_OK) {
            log.debug("Plan aggregation section '{}' degraded to null (status={})", section, status.get());
        }
    }
}
