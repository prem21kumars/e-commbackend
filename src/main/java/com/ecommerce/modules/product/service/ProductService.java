package com.ecommerce.modules.product.service;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.response.PagedResponse;
import com.ecommerce.modules.category.entity.Category;
import com.ecommerce.modules.category.repository.CategoryRepository;
import com.ecommerce.modules.inventory.entity.Inventory;
import com.ecommerce.modules.inventory.entity.InventoryMovement;
import com.ecommerce.modules.inventory.entity.MovementType;
import com.ecommerce.modules.inventory.repository.InventoryMovementRepository;
import com.ecommerce.modules.inventory.repository.InventoryRepository;
import com.ecommerce.modules.product.dto.*;
import com.ecommerce.modules.product.entity.*;
import com.ecommerce.modules.product.repository.ProductImageRepository;
import com.ecommerce.modules.product.repository.ProductRepository;
import com.ecommerce.modules.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository imageRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;

    @Transactional(readOnly = true)
    public PagedResponse<ProductSummaryDto> getProducts(
            ProductStatus status,
            Long categoryId,
            Boolean featured,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String search,
            Pageable pageable
    ) {
        Page<Product> productPage = productRepository.searchProducts(
                status != null ? status : ProductStatus.PUBLISHED,
                categoryId,
                featured,
                minPrice,
                maxPrice,
                search,
                pageable
        );

        List<ProductSummaryDto> dtos = productPage.getContent().stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());

        return PagedResponse.<ProductSummaryDto>builder()
                .content(dtos)
                .pageNumber(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .isFirst(productPage.isFirst())
                .isLast(productPage.isLast())
                .hasNext(productPage.hasNext())
                .hasPrevious(productPage.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#slug")
    public ProductDetailDto getProductBySlug(String slug) {
        Product product = productRepository.findPublishedBySlugWithDetails(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));

        List<ProductSummaryDto> related = getRelatedProducts(product.getId(), product.getCategory().getId());
        return mapToDetailDto(product, related);
    }

    @Transactional(readOnly = true)
    public ProductDetailDto getProductById(Long id) {
        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        List<ProductSummaryDto> related = getRelatedProducts(product.getId(), product.getCategory().getId());
        return mapToDetailDto(product, related);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDto> getFeaturedProducts() {
        return productRepository.findTop8ByFeaturedTrueAndStatusOrderByCreatedAtDesc(ProductStatus.PUBLISHED)
                .stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDto> getRelatedProducts(Long productId, Long categoryId) {
        return productRepository.findTop4ByCategoryIdAndIdNotAndStatus(categoryId, productId, ProductStatus.PUBLISHED)
                .stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductDetailDto createProduct(CreateProductRequest request) {
        String slug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? request.getSlug()
                : generateSlug(request.getName());

        if (productRepository.existsBySlug(slug)) {
            throw new ConflictException("Product with slug '" + slug + "' already exists");
        }
        if (productRepository.existsBySku(request.getSku())) {
            throw new ConflictException("Product with SKU '" + request.getSku() + "' already exists");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

        Product product = Product.builder()
                .name(request.getName().trim())
                .slug(slug)
                .sku(request.getSku().trim().toUpperCase())
                .description(request.getDescription())
                .shortDescription(request.getShortDescription())
                .category(category)
                .basePrice(request.getBasePrice())
                .salePrice(request.getSalePrice())
                .costPrice(request.getCostPrice())
                .status(request.getStatus())
                .featured(request.isFeatured())
                .weightGrams(request.getWeightGrams())
                .dimensionsCm(request.getDimensionsCm())
                .build();

        // Attach images
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<ProductImage> images = request.getImages().stream()
                    .map(imgDto -> ProductImage.builder()
                            .product(product)
                            .imageUrl(imgDto.getImageUrl())
                            .thumbnailUrl(imgDto.getThumbnailUrl())
                            .altText(imgDto.getAltText())
                            .displayOrder(imgDto.getDisplayOrder())
                            .primary(imgDto.isPrimary())
                            .build())
                    .collect(Collectors.toList());
            product.setImages(images);
        }

        // Initialize inventory
        Inventory inventory = Inventory.builder()
                .product(product)
                .quantityAvailable(request.getInitialStock())
                .quantityReserved(0)
                .lowStockThreshold(5)
                .build();
        product.setInventory(inventory);

        Product savedProduct = productRepository.save(product);

        if (request.getInitialStock() > 0) {
            InventoryMovement movement = InventoryMovement.builder()
                    .product(savedProduct)
                    .quantityChange(request.getInitialStock())
                    .resultingQuantity(request.getInitialStock())
                    .movementType(MovementType.RESTOCK)
                    .notes("Initial stock on product creation")
                    .createdBy("SYSTEM")
                    .build();
            movementRepository.save(movement);
        }

        return mapToDetailDto(savedProduct, Collections.emptyList());
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductDetailDto updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (productRepository.existsBySlugAndIdNot(request.getSlug(), id)) {
            throw new ConflictException("Product with slug '" + request.getSlug() + "' already exists");
        }
        if (productRepository.existsBySkuAndIdNot(request.getSku(), id)) {
            throw new ConflictException("Product with SKU '" + request.getSku() + "' already exists");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

        product.setName(request.getName().trim());
        product.setSlug(request.getSlug().trim());
        product.setSku(request.getSku().trim().toUpperCase());
        product.setDescription(request.getDescription());
        product.setShortDescription(request.getShortDescription());
        product.setCategory(category);
        product.setBasePrice(request.getBasePrice());
        product.setSalePrice(request.getSalePrice());
        product.setCostPrice(request.getCostPrice());
        product.setStatus(request.getStatus());
        product.setFeatured(request.isFeatured());
        product.setWeightGrams(request.getWeightGrams());
        product.setDimensionsCm(request.getDimensionsCm());

        // Update images if provided
        if (request.getImages() != null) {
            imageRepository.deleteByProductId(id);
            List<ProductImage> images = request.getImages().stream()
                    .map(imgDto -> ProductImage.builder()
                            .product(product)
                            .imageUrl(imgDto.getImageUrl())
                            .thumbnailUrl(imgDto.getThumbnailUrl())
                            .altText(imgDto.getAltText())
                            .displayOrder(imgDto.getDisplayOrder())
                            .primary(imgDto.isPrimary())
                            .build())
                    .collect(Collectors.toList());
            imageRepository.saveAll(images);
            product.setImages(images);
        }

        Product updated = productRepository.save(product);
        List<ProductSummaryDto> related = getRelatedProducts(id, category.getId());
        return mapToDetailDto(updated, related);
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        // Soft archive
        product.setStatus(ProductStatus.ARCHIVED);
        productRepository.save(product);
    }

    private ProductSummaryDto mapToSummaryDto(Product p) {
        int available = (p.getInventory() != null) ? p.getInventory().getAvailableToSell() : 0;
        String primaryImage = p.getImages().stream()
                .filter(ProductImage::isPrimary)
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(p.getImages().isEmpty() ? null : p.getImages().getFirst().getImageUrl());

        return ProductSummaryDto.builder()
                .id(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                .sku(p.getSku())
                .shortDescription(p.getShortDescription())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .categorySlug(p.getCategory() != null ? p.getCategory().getSlug() : null)
                .basePrice(p.getBasePrice())
                .salePrice(p.getSalePrice())
                .effectivePrice(p.getEffectivePrice())
                .status(p.getStatus())
                .featured(p.isFeatured())
                .primaryImageUrl(primaryImage)
                .averageRating(p.getAverageRating())
                .totalReviews(p.getTotalReviews())
                .availableStock(available)
                .inStock(available > 0)
                .build();
    }

    private ProductDetailDto mapToDetailDto(Product p, List<ProductSummaryDto> related) {
        int available = (p.getInventory() != null) ? p.getInventory().getAvailableToSell() : 0;

        List<ProductImageDto> imageDtos = p.getImages().stream()
                .map(img -> ProductImageDto.builder()
                        .id(img.getId())
                        .imageUrl(img.getImageUrl())
                        .thumbnailUrl(img.getThumbnailUrl())
                        .altText(img.getAltText())
                        .displayOrder(img.getDisplayOrder())
                        .primary(img.isPrimary())
                        .build())
                .collect(Collectors.toList());

        List<ProductVariantDto> variantDtos = p.getVariants() != null
                ? p.getVariants().stream()
                .map(v -> ProductVariantDto.builder()
                        .id(v.getId())
                        .sku(v.getSku())
                        .variantName(v.getVariantName())
                        .priceAdjustment(v.getPriceAdjustment())
                        .active(v.isActive())
                        .build())
                .collect(Collectors.toList())
                : Collections.emptyList();

        return ProductDetailDto.builder()
                .id(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                .sku(p.getSku())
                .description(p.getDescription())
                .shortDescription(p.getShortDescription())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .categorySlug(p.getCategory() != null ? p.getCategory().getSlug() : null)
                .basePrice(p.getBasePrice())
                .salePrice(p.getSalePrice())
                .effectivePrice(p.getEffectivePrice())
                .status(p.getStatus())
                .featured(p.isFeatured())
                .weightGrams(p.getWeightGrams())
                .dimensionsCm(p.getDimensionsCm())
                .averageRating(p.getAverageRating())
                .totalReviews(p.getTotalReviews())
                .availableStock(available)
                .inStock(available > 0)
                .images(imageDtos)
                .variants(variantDtos)
                .relatedProducts(related)
                .build();
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
}

