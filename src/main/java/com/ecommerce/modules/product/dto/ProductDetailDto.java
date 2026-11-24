package com.ecommerce.modules.product.dto;

import com.ecommerce.modules.product.entity.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailDto {
    private Long id;
    private String name;
    private String slug;
    private String sku;
    private String description;
    private String shortDescription;
    private Long categoryId;
    private String categoryName;
    private String categorySlug;
    private BigDecimal basePrice;
    private BigDecimal salePrice;
    private BigDecimal effectivePrice;
    private ProductStatus status;
    private boolean featured;
    private int weightGrams;
    private String dimensionsCm;
    private BigDecimal averageRating;
    private int totalReviews;
    private int availableStock;
    private boolean inStock;
    private List<ProductImageDto> images;
    private List<ProductVariantDto> variants;
    private List<ProductSummaryDto> relatedProducts;
}

