package com.ecommerce.modules.product.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PagedResponse;
import com.ecommerce.modules.product.dto.CreateProductRequest;
import com.ecommerce.modules.product.dto.ProductDetailDto;
import com.ecommerce.modules.product.dto.ProductSummaryDto;
import com.ecommerce.modules.product.dto.UpdateProductRequest;
import com.ecommerce.modules.product.entity.ProductStatus;
import com.ecommerce.modules.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Product Catalog", description = "Endpoints for browsing products, specifications, and administrative catalog management")
public class ProductController {

    private final ProductService productService;

    @GetMapping("/products")
    @Operation(summary = "Search and Filter Products", description = "Paginated catalog browsing with price ranges, category filtering, search, and sorting")
    public ResponseEntity<ApiResponse<PagedResponse<ProductSummaryDto>>> getProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedResponse<ProductSummaryDto> response = productService.getProducts(
                ProductStatus.PUBLISHED,
                categoryId,
                featured,
                minPrice,
                maxPrice,
                search,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Get Product by ID", description = "Retrieves full product details including images, variants, and related items")
    public ResponseEntity<ApiResponse<ProductDetailDto>> getProductById(@PathVariable Long id) {
        ProductDetailDto product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @GetMapping("/products/slug/{slug}")
    @Operation(summary = "Get Product by Slug", description = "SEO-friendly product detail lookup by unique URL slug")
    public ResponseEntity<ApiResponse<ProductDetailDto>> getProductBySlug(@PathVariable String slug) {
        ProductDetailDto product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @GetMapping("/products/featured")
    @Operation(summary = "Featured Products", description = "Retrieves top highlighted products for storefront homepage")
    public ResponseEntity<ApiResponse<List<ProductSummaryDto>>> getFeaturedProducts() {
        List<ProductSummaryDto> featured = productService.getFeaturedProducts();
        return ResponseEntity.ok(ApiResponse.success(featured));
    }

    @PostMapping("/admin/products")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create Product (Admin)", description = "Creates a new catalog product with initial inventory and images")
    public ResponseEntity<ApiResponse<ProductDetailDto>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductDetailDto created = productService.createProduct(request);
        return new ResponseEntity<>(ApiResponse.success("Product created successfully", created), HttpStatus.CREATED);
    }

    @PutMapping("/admin/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update Product (Admin)", description = "Updates product details, pricing, images, and category association")
    public ResponseEntity<ApiResponse<ProductDetailDto>> updateProduct(@PathVariable Long id,
                                                                       @Valid @RequestBody UpdateProductRequest request) {
        ProductDetailDto updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", updated));
    }

    @DeleteMapping("/admin/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Archive Product (Admin)", description = "Soft-archives a product by switching status to ARCHIVED")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.messageOnly("Product archived successfully"));
    }
}

