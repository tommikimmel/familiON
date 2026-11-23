package com.familion.finance.categories.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.familion.common.error.BusinessRuleException;
import com.familion.common.error.NotFoundException;
import com.familion.finance.categories.application.api.CategoryRef;
import com.familion.finance.categories.domain.Category;
import com.familion.finance.categories.domain.CategoryKind;
import com.familion.finance.categories.infrastructure.persistence.CategoryRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CategoryQueryServiceTest {

    private CategoryRepository categoryRepository;
    private CategoryQueryService queryService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        queryService = new CategoryQueryService(categoryRepository);
    }

    @Test
    void requireUsableReturnsRefForActiveFamilyOrSystemCategoryWithMatchingKind() {
        UUID familyId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Category custom = Category.createCustom(
                familyId, null, "Veterinaria", CategoryKind.EXPENSE, "paw-print", "#F97316");
        setField(custom, "id", categoryId);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(custom));

        CategoryRef ref = queryService.requireUsable(familyId, categoryId, "EXPENSE");

        assertThat(ref).isEqualTo(
                new CategoryRef(categoryId, "Veterinaria", "EXPENSE", "paw-print", "#F97316"));
    }

    @Test
    void requireUsableThrows422WhenKindMismatchesOrCategoryInactiveOrOtherFamily() {
        UUID familyId = UUID.randomUUID();
        UUID otherFamilyId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();

        Category expense = Category.createCustom(familyId, null, "Club", CategoryKind.EXPENSE, null, null);
        setField(expense, "id", catId);
        when(categoryRepository.findById(catId)).thenReturn(Optional.of(expense));

        assertThatThrownBy(() -> queryService.requireUsable(familyId, catId, "INCOME"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> queryService.requireUsable(otherFamilyId, catId, "EXPENSE"))
                .isInstanceOf(BusinessRuleException.class);

        expense.update(null, null, null, false);
        assertThatThrownBy(() -> queryService.requireUsable(familyId, catId, "EXPENSE"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void requireSystemCategoryReturnsMatchingSystemCategoryAndThrows404WhenMissing() {
        UUID fuelId = UUID.randomUUID();
        Category fuel = Category.createCustom(UUID.randomUUID(), null, "Combustible", CategoryKind.EXPENSE, "fuel", null);
        setField(fuel, "id", fuelId);
        setField(fuel, "familyId", null);
        setField(fuel, "system", true);
        setField(fuel, "systemKey", "VEHICLES_FUEL");
        when(categoryRepository.findBySystemKeyAndSystemTrueAndActiveTrue("VEHICLES_FUEL"))
                .thenReturn(Optional.of(fuel));
        when(categoryRepository.findBySystemKeyAndSystemTrueAndActiveTrue("UNKNOWN"))
                .thenReturn(Optional.empty());

        CategoryRef ref = queryService.requireSystemCategory("VEHICLES_FUEL");
        assertThat(ref.id()).isEqualTo(fuelId);
        assertThat(ref.name()).isEqualTo("Combustible");
        assertThat(ref.kind()).isEqualTo("EXPENSE");

        assertThatThrownBy(() -> queryService.requireSystemCategory("UNKNOWN"))
                .isInstanceOf(NotFoundException.class);
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
