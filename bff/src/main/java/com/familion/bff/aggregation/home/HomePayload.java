package com.familion.bff.aggregation.home;

import com.fasterxml.jackson.databind.JsonNode;

public record HomePayload(
        JsonNode summary,
        JsonNode byCategory,
        JsonNode byPerson,
        JsonNode recentTransactions,
        JsonNode latestRates,
        Long unreadNotifications) {
}
