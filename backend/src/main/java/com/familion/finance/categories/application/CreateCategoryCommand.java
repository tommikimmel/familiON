package com.familion.finance.categories.application;

import com.familion.finance.categories.domain.CategoryKind;
import java.util.UUID;

public record CreateCategoryCommand(
        String name,
        CategoryKind kind,
        UUID parentCategoryId,
        String icon,
        String colorHex) {}
