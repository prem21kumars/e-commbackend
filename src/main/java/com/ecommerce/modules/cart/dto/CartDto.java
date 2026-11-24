package com.ecommerce.modules.cart.dto;

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
public class CartDto {
    private Long id;
    private Long userId;
    private List<CartItemDto> items;
    private int totalQuantity;
    private BigDecimal subtotal;
    private BigDecimal estimatedShipping;
    private BigDecimal totalAmount;
    private boolean allItemsInStock;
}

