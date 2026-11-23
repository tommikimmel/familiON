package com.familion.finance.categories.application.api;

import java.util.UUID;

public interface CategoryQueryApi {

    CategoryRef requireUsable(UUID familyId, UUID categoryId, String kind);

    CategoryRef requireSystemCategory(String systemKey);
}
