package com.familion.finance.categories.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.ForbiddenException;
import com.familion.common.error.NotFoundException;
import com.familion.finance.categories.domain.Category;
import com.familion.finance.categories.domain.CategoryKind;
import com.familion.finance.categories.infrastructure.persistence.CategoryRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CategoryServiceTest {

    private CategoryRepository categoryRepository;
    private CategoryCache categoryCache;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        categoryCache = mock(CategoryCache.class);
        categoryService = new CategoryService(categoryRepository, categoryCache);
    }

    @Test
    void listCategoriesReturnsCachedValuesFilteredByKindWithoutHittingDb() {
        UUID familyId = UUID.randomUUID();
        CategoryView expense = new CategoryView(
                UUID.randomUUID(), null, "Supermercado", CategoryKind.EXPENSE, "shopping-cart", null, true, "SUPERMARKET");
        CategoryView income = new CategoryView(
                UUID.randomUUID(), null, "Sueldo", CategoryKind.INCOME, "briefcase", null, true, "SALARY");
        when(categoryCache.get(familyId)).thenReturn(Optional.of(List.of(expense, income)));

        List<CategoryView> onlyExpense = categoryService.listCategories(familyId, CategoryKind.EXPENSE);

        assertThat(onlyExpense).containsExactly(expense);
        verify(categoryRepository, never()).findActiveVisibleToFamilyOrdered(any());
    }

    @Test
    void listCategoriesLoadsFromDbAndCachesOnCacheMiss() {
        UUID familyId = UUID.randomUUID();
        Category custom = withId(
                Category.createCustom(familyId, null, "Club", CategoryKind.EXPENSE, "trophy", "#10B981"),
                UUID.randomUUID());
        when(categoryCache.get(familyId)).thenReturn(Optional.empty());
        when(categoryRepository.findActiveVisibleToFamilyOrdered(familyId)).thenReturn(List.of(custom));

        List<CategoryView> result = categoryService.listCategories(familyId, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Club");
        verify(categoryCache).put(familyId, result);
    }

    @Test
    void createCategoryRejectsChildOfChildWith422() {
        UUID familyId = UUID.randomUUID();
        UUID grandParentId = UUID.randomUUID();
        UUID childParentId = UUID.randomUUID();
        Category childParent = withId(
                Category.createCustom(familyId, grandParentId, "Sub", CategoryKind.EXPENSE, null, null),
                childParentId);
        when(categoryRepository.findById(childParentId)).thenReturn(Optional.of(childParent));

        CreateCategoryCommand command =
                new CreateCategoryCommand("Grandchild", CategoryKind.EXPENSE, childParentId, null, null);

        assertThatThrownBy(() -> categoryService.createCategory(familyId, command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("maximum depth is 2");
        verify(categoryRepository, never()).saveAndFlush(any());
        verify(categoryCache, never()).evict(any());
    }

    @Test
    void createCategoryRejectsParentWithDifferentKind() {
        UUID familyId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Category parent = withId(
                Category.createCustom(familyId, null, "Sueldo extra", CategoryKind.INCOME, null, null),
                parentId);
        when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));

        CreateCategoryCommand command =
                new CreateCategoryCommand("Subgasto", CategoryKind.EXPENSE, parentId, null, null);

        assertThatThrownBy(() -> categoryService.createCategory(familyId, command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("same kind");
    }

    @Test
    void createCategoryRejectsInactiveParentOrOtherFamilyParent() {
        UUID familyId = UUID.randomUUID();
        UUID otherFamilyId = UUID.randomUUID();
        UUID otherParentId = UUID.randomUUID();
        Category otherParent = withId(
                Category.createCustom(otherFamilyId, null, "Other", CategoryKind.EXPENSE, null, null),
                otherParentId);
        when(categoryRepository.findById(otherParentId)).thenReturn(Optional.of(otherParent));

        assertThatThrownBy(() -> categoryService.createCategory(
                        familyId,
                        new CreateCategoryCommand("Sub", CategoryKind.EXPENSE, otherParentId, null, null)))
                .isInstanceOf(BusinessRuleException.class);

        UUID inactiveParentId = UUID.randomUUID();
        Category inactiveParent = withId(
                Category.createCustom(familyId, null, "Inactive", CategoryKind.EXPENSE, null, null),
                inactiveParentId);
        inactiveParent.update(null, null, null, false);
        when(categoryRepository.findById(inactiveParentId)).thenReturn(Optional.of(inactiveParent));

        assertThatThrownBy(() -> categoryService.createCategory(
                        familyId,
                        new CreateCategoryCommand("Sub", CategoryKind.EXPENSE, inactiveParentId, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createCategorySavesAndEvictsCache() {
        UUID familyId = UUID.randomUUID();
        UUID savedId = UUID.randomUUID();
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            return withId(c, savedId);
        });

        CategoryView created = categoryService.createCategory(
                familyId,
                new CreateCategoryCommand("  Colegio  ", CategoryKind.EXPENSE, null, "book-open", "#2563EB"));

        assertThat(created.id()).isEqualTo(savedId);
        assertThat(created.name()).isEqualTo("Colegio");
        assertThat(created.icon()).isEqualTo("book-open");
        assertThat(created.colorHex()).isEqualTo("#2563EB");
        assertThat(created.system()).isFalse();
        assertThat(created.systemKey()).isNull();
        verify(categoryCache).evict(familyId);
    }

    @Test
    void updateCategoryRejectsSystemCategoryWith403() {
        UUID familyId = UUID.randomUUID();
        UUID systemCategoryId = UUID.randomUUID();
        Category systemCat = systemCategory(systemCategoryId, "SUPERMARKET", CategoryKind.EXPENSE);
        when(categoryRepository.findById(systemCategoryId)).thenReturn(Optional.of(systemCat));

        assertThatThrownBy(() -> categoryService.updateCategory(
                        familyId, systemCategoryId, new UpdateCategoryCommand("Renamed", null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(categoryCache, never()).evict(any());
    }

    @Test
    void updateCategoryRejectsOtherFamilyCategoryWith404() {
        UUID familyId = UUID.randomUUID();
        UUID otherFamilyId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        Category otherCat = withId(
                Category.createCustom(otherFamilyId, null, "Secret", CategoryKind.EXPENSE, null, null),
                catId);
        when(categoryRepository.findById(catId)).thenReturn(Optional.of(otherCat));

        assertThatThrownBy(() -> categoryService.updateCategory(
                        familyId, catId, new UpdateCategoryCommand("Hacked", null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateCategoryUpdatesFieldsAndEvictsCache() {
        UUID familyId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        Category existing = withId(
                Category.createCustom(familyId, null, "Deportes", CategoryKind.EXPENSE, "dumbbell", "#111111"),
                catId);
        when(categoryRepository.findById(catId)).thenReturn(Optional.of(existing));
        when(categoryRepository.saveAndFlush(existing)).thenReturn(existing);

        CategoryView updated = categoryService.updateCategory(
                familyId, catId, new UpdateCategoryCommand("Gimnasio", "heart", "#22C55E", false));

        assertThat(updated.name()).isEqualTo("Gimnasio");
        assertThat(updated.icon()).isEqualTo("heart");
        assertThat(updated.colorHex()).isEqualTo("#22C55E");
        assertThat(existing.isActive()).isFalse();
        verify(categoryCache).evict(familyId);
    }

    private static Category withId(Category category, UUID id) {
        setField(category, "id", id);
        return category;
    }

    private static Category systemCategory(UUID id, String systemKey, CategoryKind kind) {
        Category c = Category.createCustom(UUID.randomUUID(), null, "Supermercado", kind, "shopping-cart", null);
        setField(c, "id", id);
        setField(c, "familyId", null);
        setField(c, "system", true);
        setField(c, "systemKey", systemKey);
        return c;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
