package com.ecommerce.modules.shipping.dto;

import com.ecommerce.modules.shipping.entity.ShipmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentDto {
    private Long id;
    private Long orderId;
    private String orderNumber;
    private String carrier;
    private String trackingNumber;
    private ShipmentStatus status;
    private String shippingMethod;
    private Instant shippedAt;
    private Instant estimatedDelivery;
    private Instant deliveredAt;
    private List<ShipmentTrackingDto> trackingHistory;
}

