package com.familion.finance.categories.infrastructure.web;

import com.familion.finance.categories.domain.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 80, message = "Category name must be at most 80 characters")
        String name,
        @NotNull(message = "Category kind is required") CategoryKind kind,
        UUID parentCategoryId,
        @Size(max = 50, message = "Icon name must be at most 50 characters") String icon,
        @Pattern(
                regexp = "^#[0-9A-Fa-f]{6}$",
                message = "Color must be a valid hex color (#RRGGBB)")
        String colorHex) {}
