package com.ecommerce.category;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.modules.category.dto.UpdateCategoryRequest;
import com.ecommerce.modules.category.entity.Category;
import com.ecommerce.modules.category.repository.CategoryRepository;
import com.ecommerce.modules.category.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService Unit Tests")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("Circular hierarchy detection: cannot set category parent to itself")
    void testCannotSetParentToItself() {
        Category cat = Category.builder().id(10L).name("Toys").slug("toys").build();
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(cat));

        UpdateCategoryRequest req = UpdateCategoryRequest.builder()
                .name("Toys")
                .slug("toys")
                .parentId(10L) // same id
                .build();

        assertThrows(BadRequestException.class, () -> categoryService.updateCategory(10L, req));
    }

    @Test
    @DisplayName("Circular hierarchy detection: cannot set parent to a descendant child")
    void testCircularParentRejection() {
        Category grandparent = Category.builder().id(1L).name("Grandparent").slug("gp").build();
        Category parent = Category.builder().id(2L).name("Parent").slug("p").parent(grandparent).build();
        Category child = Category.builder().id(3L).name("Child").slug("c").parent(parent).build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(grandparent));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(child));

        // Attempt to make grandparent a child of its own grandchild
        UpdateCategoryRequest req = UpdateCategoryRequest.builder()
                .name("Grandparent")
                .slug("gp")
                .parentId(3L)
                .build();

        assertThrows(BadRequestException.class, () -> categoryService.updateCategory(1L, req));
    }
}

