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
public class UpdateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
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

    private BigDecimal salePrice;

    private BigDecimal costPrice;

    @NotNull(message = "Product status is required")
    private ProductStatus status;

    private boolean featured;

    private int weightGrams;

    private String dimensionsCm;

    private List<ProductImageDto> images;
}

