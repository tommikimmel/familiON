package com.familion.finance.categories.application;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.finance.categories.domain.Category;
import com.familion.finance.categories.domain.CategoryKind;
import com.familion.finance.categories.infrastructure.persistence.CategoryRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryCache categoryCache;

    public CategoryService(CategoryRepository categoryRepository, CategoryCache categoryCache) {
        this.categoryRepository = categoryRepository;
        this.categoryCache = categoryCache;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listCategories(UUID familyId, CategoryKind kind) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        List<CategoryView> allCategories = categoryCache
                .get(familyId)
                .orElseGet(() -> loadAndCacheCategories(familyId));

        if (kind == null) {
            return allCategories;
        }
        return allCategories.stream().filter(category -> category.kind() == kind).toList();
    }

    @Transactional
    public CategoryView createCategory(UUID familyId, CreateCategoryCommand command) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        if (command.parentCategoryId() != null) {
            validateParentCategory(familyId, command.parentCategoryId(), command.kind());
        }

        Category category = Category.createCustom(
                familyId,
                command.parentCategoryId(),
                command.name(),
                command.kind(),
                command.icon(),
                command.colorHex());
        Category saved = categoryRepository.saveAndFlush(category);
        evictAfterCommitOrNow(familyId);
        return CategoryView.from(saved);
    }

    @Transactional
    public CategoryView updateCategory(
            UUID familyId, UUID categoryId, UpdateCategoryCommand command) {
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(categoryId, "categoryId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        Category category = categoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category not found"));

        if (category.isSystem()) {
            throw new ForbiddenException("System categories cannot be edited");
        }
        if (!familyId.equals(category.getFamilyId())) {
            throw new NotFoundException("Category not found");
        }

        category.update(command.name(), command.icon(), command.colorHex(), command.isActive());
        Category saved = categoryRepository.saveAndFlush(category);
        evictAfterCommitOrNow(familyId);
        return CategoryView.from(saved);
    }

    private void validateParentCategory(UUID familyId, UUID parentCategoryId, CategoryKind kind) {
        Category parent = categoryRepository
                .findById(parentCategoryId)
                .orElseThrow(() -> new BusinessRuleException("Parent category is not usable"));

        if (!parent.isVisibleTo(familyId) || !parent.isActive()) {
            throw new BusinessRuleException("Parent category is not usable");
        }
        if (parent.getKind() != kind) {
            throw new BusinessRuleException("Parent category must have the same kind");
        }
        if (parent.getParentCategoryId() != null) {
            throw new BusinessRuleException(
                    "Parent category must be a top-level category (maximum depth is 2)");
        }
    }

    private List<CategoryView> loadAndCacheCategories(UUID familyId) {
        List<CategoryView> loaded = categoryRepository
                .findActiveVisibleToFamilyOrdered(familyId)
                .stream()
                .map(CategoryView::from)
                .toList();
        categoryCache.put(familyId, loaded);
        return loaded;
    }

    private void evictAfterCommitOrNow(UUID familyId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            categoryCache.evict(familyId);
                        }
                    });
        } else {
            categoryCache.evict(familyId);
        }
    }
}
