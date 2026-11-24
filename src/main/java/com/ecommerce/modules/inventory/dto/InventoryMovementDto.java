package com.ecommerce.modules.inventory.dto;

import com.ecommerce.modules.inventory.entity.MovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovementDto {
    private Long id;
    private Long productId;
    private int quantityChange;
    private int resultingQuantity;
    private MovementType movementType;
    private String referenceId;
    private String notes;
    private String createdBy;
    private Instant createdAt;
}

