package com.familion.finance.categories.application;

public record UpdateCategoryCommand(
        String name,
        String icon,
        String colorHex,
        Boolean isActive) {}
