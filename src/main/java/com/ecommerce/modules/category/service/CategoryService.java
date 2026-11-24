package com.ecommerce.modules.category.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.modules.category.dto.CategoryDto;
import com.ecommerce.modules.category.dto.CreateCategoryRequest;
import com.ecommerce.modules.category.dto.UpdateCategoryRequest;
import com.ecommerce.modules.category.entity.Category;
import com.ecommerce.modules.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "categories", key = "'tree'")
    public List<CategoryDto> getCategoryTree() {
        List<Category> rootCategories = categoryRepository.findAllRootCategoriesWithSubcategories();
        return rootCategories.stream()
                .map(this::mapToTreeDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getAllFlatCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(this::mapToFlatDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryDto getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
        return mapToTreeDto(category);
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryDto createCategory(CreateCategoryRequest request) {
        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? request.getSlug()
                : generateSlug(request.getName());

        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Category with slug '" + slug + "' already exists");
        }

        Category parent = null;
        if (request.getParentId() != null) {
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Category", "id", request.getParentId()));
        }

        Category category = Category.builder()
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .parent(parent)
                .displayOrder(request.getDisplayOrder())
                .active(true)
                .build();

        Category saved = categoryRepository.save(category);
        return mapToFlatDto(saved);
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryDto updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        if (categoryRepository.existsBySlugAndIdNot(request.getSlug(), id)) {
            throw new ConflictException("Category with slug '" + request.getSlug() + "' already exists");
        }

        Category parent = null;
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new BadRequestException("A category cannot be its own parent");
            }
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Category", "id", request.getParentId()));

            // Circular reference check
            if (isDescendant(category, parent)) {
                throw new BadRequestException("Circular hierarchy detected: cannot set parent to a child category");
            }
        }

        category.setName(request.getName().trim());
        category.setSlug(request.getSlug().trim());
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());
        category.setParent(parent);
        category.setDisplayOrder(request.getDisplayOrder());
        category.setActive(request.isActive());

        Category updated = categoryRepository.save(category);
        return mapToFlatDto(updated);
    }

    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));

        // Soft delete / cascade deactivation
        category.setActive(false);
        categoryRepository.save(category);
    }

    private boolean isDescendant(Category potentialAncestor, Category target) {
        Category current = target;
        while (current != null) {
            if (current.getId().equals(potentialAncestor.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private String generateSlug(String input) {
        String nonLatin = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return nonLatin.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private CategoryDto mapToFlatDto(Category cat) {
        return CategoryDto.builder()
                .id(cat.getId())
                .name(cat.getName())
                .slug(cat.getSlug())
                .description(cat.getDescription())
                .imageUrl(cat.getImageUrl())
                .parentId(cat.getParent() != null ? cat.getParent().getId() : null)
                .parentName(cat.getParent() != null ? cat.getParent().getName() : null)
                .displayOrder(cat.getDisplayOrder())
                .active(cat.isActive())
                .build();
    }

    private CategoryDto mapToTreeDto(Category cat) {
        List<CategoryDto> children = cat.getSubcategories() != null
                ? cat.getSubcategories().stream()
                .filter(Category::isActive)
                .sorted(Comparator.comparingInt(Category::getDisplayOrder))
                .map(this::mapToTreeDto)
                .collect(Collectors.toList())
                : Collections.emptyList();

        return CategoryDto.builder()
                .id(cat.getId())
                .name(cat.getName())
                .slug(cat.getSlug())
                .description(cat.getDescription())
                .imageUrl(cat.getImageUrl())
                .parentId(cat.getParent() != null ? cat.getParent().getId() : null)
                .parentName(cat.getParent() != null ? cat.getParent().getName() : null)
                .displayOrder(cat.getDisplayOrder())
                .active(cat.isActive())
                .subcategories(children)
                .build();
    }
}

