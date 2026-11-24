package com.ecommerce.modules.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShipmentRequest {

    @NotBlank(message = "Carrier name is required")
    private String carrier;

    @NotBlank(message = "Tracking number is required")
    private String trackingNumber;

    @Builder.Default
    private String shippingMethod = "STANDARD";

    private Instant estimatedDelivery;
}

