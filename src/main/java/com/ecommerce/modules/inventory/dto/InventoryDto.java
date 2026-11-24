package com.ecommerce.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDto {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private int quantityAvailable;
    private int quantityReserved;
    private int availableToSell;
    private int lowStockThreshold;
    private boolean lowStock;
}

