package com.ecommerce.modules.category.repository;

import com.ecommerce.modules.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    List<Category> findByParentIsNullAndActiveTrueOrderByDisplayOrderAsc();

    List<Category> findByActiveTrueOrderByDisplayOrderAsc();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.subcategories WHERE c.parent IS NULL AND c.active = true ORDER BY c.displayOrder ASC")
    List<Category> findAllRootCategoriesWithSubcategories();
}

