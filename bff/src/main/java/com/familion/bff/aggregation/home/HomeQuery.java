package com.familion.bff.aggregation.home;

public record HomeQuery(
        String view,
        String lens,
        String granularity,
        String date) {

    public boolean isFamilyView() {
        return "FAMILY".equals(view);
    }
}
