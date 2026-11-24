package com.ecommerce.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAvailabilityDto {
    private Long productId;
    private String sku;
    private int availableToSell;
    private boolean inStock;
}

