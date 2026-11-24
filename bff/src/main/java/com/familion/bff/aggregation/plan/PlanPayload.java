package com.familion.bff.aggregation.plan;

import com.fasterxml.jackson.databind.JsonNode;

public record PlanPayload(
        JsonNode positions,
        JsonNode installments,
        JsonNode commitment,
        JsonNode recurring,
        JsonNode goals,
        JsonNode allowances) {
}
