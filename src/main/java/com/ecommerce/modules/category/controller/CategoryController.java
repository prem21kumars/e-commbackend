package com.ecommerce.modules.category.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.modules.category.dto.CategoryDto;
import com.ecommerce.modules.category.dto.CreateCategoryRequest;
import com.ecommerce.modules.category.dto.UpdateCategoryRequest;
import com.ecommerce.modules.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Category Management", description = "Endpoints for viewing and administrating hierarchical category trees")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/categories")
    @Operation(summary = "List Categories", description = "Returns active categories either as a nested hierarchy tree or flat list")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> getCategories(
            @RequestParam(required = false, defaultValue = "true") boolean hierarchical) {
        List<CategoryDto> result = hierarchical
                ? categoryService.getCategoryTree()
                : categoryService.getAllFlatCategories();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/categories/{slug}")
    @Operation(summary = "Get Category by Slug", description = "Retrieves category details and its direct subcategories by slug")
    public ResponseEntity<ApiResponse<CategoryDto>> getCategoryBySlug(@PathVariable String slug) {
        CategoryDto category = categoryService.getCategoryBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create Category (Admin)", description = "Creates a new catalog category or subcategory")
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryDto created = categoryService.createCategory(request);
        return new ResponseEntity<>(ApiResponse.success("Category created successfully", created), HttpStatus.CREATED);
    }

    @PutMapping("/admin/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update Category (Admin)", description = "Updates category details and verifies circular hierarchy safety")
    public ResponseEntity<ApiResponse<CategoryDto>> updateCategory(@PathVariable Long id,
                                                                   @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryDto updated = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", updated));
    }

    @DeleteMapping("/admin/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Delete Category (Admin)", description = "Deactivates a category")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.messageOnly("Category deleted successfully"));
    }
}

