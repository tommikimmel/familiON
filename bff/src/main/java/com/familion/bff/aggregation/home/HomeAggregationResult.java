package com.familion.bff.aggregation.home;

public sealed interface HomeAggregationResult {

    record Ok(HomePayload payload) implements HomeAggregationResult {
    }

    record Unauthorized() implements HomeAggregationResult {
    }
}
