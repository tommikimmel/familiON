package com.familion.finance.categories.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.NotFoundException;
import com.familion.finance.categories.application.api.CategoryQueryApi;
import com.familion.finance.categories.application.api.CategoryRef;
import com.familion.finance.categories.domain.Category;
import com.familion.finance.categories.domain.CategoryKind;
import com.familion.finance.categories.infrastructure.persistence.CategoryRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryQueryService implements CategoryQueryApi {

    private final CategoryRepository categoryRepository;

    public CategoryQueryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public CategoryRef requireUsable(UUID familyId, UUID categoryId, String kind) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        if (categoryId == null) {
            throw new BusinessRuleException("Category is required");
        }
        CategoryKind expectedKind = parseKind(kind);

        Category category = categoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("Category is not usable"));

        if (!category.isVisibleTo(familyId)
                || !category.isActive()
                || category.getKind() != expectedKind) {
            throw new BusinessRuleException("Category is not usable");
        }

        return toCategoryRef(category);
    }

    @Override
    public CategoryRef requireSystemCategory(String systemKey) {
        if (systemKey == null || systemKey.isBlank()) {
            throw new NotFoundException("System category not found");
        }
        Category category = categoryRepository
                .findBySystemKeyAndSystemTrueAndActiveTrue(systemKey.trim())
                .orElseThrow(
                        () -> new NotFoundException("System category not found: " + systemKey));
        return toCategoryRef(category);
    }

    private static CategoryKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw new BusinessRuleException("Category kind is required");
        }
        try {
            return CategoryKind.valueOf(kind.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Invalid category kind: " + kind);
        }
    }

    private static CategoryRef toCategoryRef(Category category) {
        return new CategoryRef(
                category.getId(),
                category.getName(),
                category.getKind().name(),
                category.getIcon(),
                category.getColorHex());
    }
}
