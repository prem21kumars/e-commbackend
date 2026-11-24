package com.ecommerce.modules.inventory.dto;

import com.ecommerce.modules.inventory.entity.MovementType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustStockRequest {

    @NotNull(message = "Quantity change is required")
    private Integer quantityChange;

    @NotNull(message = "Movement type is required")
    private MovementType movementType;

    private String referenceId;

    private String notes;
}

