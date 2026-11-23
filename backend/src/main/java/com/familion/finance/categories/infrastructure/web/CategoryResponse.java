package com.familion.finance.categories.infrastructure.web;

import com.familion.finance.categories.application.CategoryView;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID parentCategoryId,
        String name,
        String kind,
        String icon,
        String colorHex,
        @JsonProperty("isSystem") boolean isSystem,
        String systemKey) {

    public static CategoryResponse from(CategoryView view) {
        return new CategoryResponse(
                view.id(),
                view.parentCategoryId(),
                view.name(),
                view.kind().name(),
                view.icon(),
                view.colorHex(),
                view.system(),
                view.systemKey());
    }
}
