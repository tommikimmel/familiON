package com.familion.finance.categories.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @Pattern(regexp = ".*\\S.*", message = "Category name must not be blank")
        @Size(max = 80, message = "Category name must be at most 80 characters")
        String name,
        @Size(max = 50, message = "Icon name must be at most 50 characters") String icon,
        @Pattern(
                regexp = "^#[0-9A-Fa-f]{6}$",
                message = "Color must be a valid hex color (#RRGGBB)")
        String colorHex,
        @JsonProperty("isActive") Boolean isActive) {}
