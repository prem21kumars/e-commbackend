package com.ecommerce.modules.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDto {
    private Long id;
    private Long productId;
    private String productName;
    private String productSlug;
    private String productSku;
    private String imageUrl;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private int availableStock;
    private boolean inStock;
}

