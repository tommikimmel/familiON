package com.familion.finance.categories.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryCache {

    Optional<List<CategoryView>> get(UUID familyId);

    void put(UUID familyId, List<CategoryView> categories);

    void evict(UUID familyId);
}
