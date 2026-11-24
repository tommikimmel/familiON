package com.familion.bff.aggregation.plan;

public sealed interface PlanAggregationResult {

    record Ok(PlanPayload payload) implements PlanAggregationResult {
    }

    record Unauthorized() implements PlanAggregationResult {
    }
}
