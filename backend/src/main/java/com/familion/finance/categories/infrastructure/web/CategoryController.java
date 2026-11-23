package com.familion.finance.categories.infrastructure.web;

import com.familion.common.security.CurrentUserProvider;
import com.familion.finance.categories.application.CategoryService;
import com.familion.finance.categories.application.CreateCategoryCommand;
import com.familion.finance.categories.application.UpdateCategoryCommand;
import com.familion.finance.categories.domain.CategoryKind;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final CurrentUserProvider currentUserProvider;

    public CategoryController(
            CategoryService categoryService, CurrentUserProvider currentUserProvider) {
        this.categoryService = categoryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<CategoryResponse> listCategories(
            @RequestParam(required = false) CategoryKind kind) {
        UUID familyId = currentUserProvider.get().familyId();
        return categoryService.listCategories(familyId, kind).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADULTO')")
    public CategoryResponse createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        UUID familyId = currentUserProvider.get().familyId();
        return CategoryResponse.from(categoryService.createCategory(
                familyId,
                new CreateCategoryCommand(
                        request.name(),
                        request.kind(),
                        request.parentCategoryId(),
                        request.icon(),
                        request.colorHex())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADULTO')")
    public CategoryResponse updateCategory(
            @PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
        UUID familyId = currentUserProvider.get().familyId();
        return CategoryResponse.from(categoryService.updateCategory(
                familyId,
                id,
                new UpdateCategoryCommand(
                        request.name(),
                        request.icon(),
                        request.colorHex(),
                        request.isActive())));
    }
}
