package com.ecommerce.modules.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessRefundRequest {

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotBlank(message = "Reason is required")
    private String reason;
}

