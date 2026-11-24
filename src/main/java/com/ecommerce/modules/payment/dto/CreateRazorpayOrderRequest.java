package com.ecommerce.modules.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRazorpayOrderRequest {

    @NotBlank(message = "Order number is required")
    private String orderNumber;
}

