package com.ecommerce.modules.product.dto;

import com.ecommerce.modules.product.entity.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummaryDto {
    private Long id;
    private String name;
    private String slug;
    private String sku;
    private String shortDescription;
    private Long categoryId;
    private String categoryName;
    private String categorySlug;
    private BigDecimal basePrice;
    private BigDecimal salePrice;
    private BigDecimal effectivePrice;
    private ProductStatus status;
    private boolean featured;
    private String primaryImageUrl;
    private BigDecimal averageRating;
    private int totalReviews;
    private int availableStock;
    private boolean inStock;
}

