package com.familion.finance.categories.application;

import com.familion.finance.categories.domain.Category;
import com.familion.finance.categories.domain.CategoryKind;
import java.util.UUID;

public record CategoryView(
        UUID id,
        UUID parentCategoryId,
        String name,
        CategoryKind kind,
        String icon,
        String colorHex,
        boolean system,
        String systemKey) {

    public static CategoryView from(Category category) {
        return new CategoryView(
                category.getId(),
                category.getParentCategoryId(),
                category.getName(),
                category.getKind(),
                category.getIcon(),
                category.getColorHex(),
                category.isSystem(),
                category.getSystemKey());
    }
}
