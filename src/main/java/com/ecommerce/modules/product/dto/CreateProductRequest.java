package com.ecommerce.modules.product.dto;

import com.ecommerce.modules.product.entity.ProductStatus;
import jakarta.validation.constraints.*;
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
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255)
    private String name;

    @Pattern(regexp = "^$|^[a-z0-9-]+$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
    private String slug;

    @NotBlank(message = "SKU is required")
    @Size(max = 100)
    private String sku;

    private String description;

    @Size(max = 500)
    private String shortDescription;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.01", message = "Base price must be greater than zero")
    private BigDecimal basePrice;

    @DecimalMin(value = "0.00", message = "Sale price cannot be negative")
    private BigDecimal salePrice;

    private BigDecimal costPrice;

    @NotNull(message = "Initial stock is required")
    @Min(value = 0, message = "Initial stock cannot be negative")
    @Builder.Default
    private int initialStock = 0;

    @Builder.Default
    private ProductStatus status = ProductStatus.PUBLISHED;

    @Builder.Default
    private boolean featured = false;

    private int weightGrams;

    private String dimensionsCm;

    private List<ProductImageDto> images;
}

