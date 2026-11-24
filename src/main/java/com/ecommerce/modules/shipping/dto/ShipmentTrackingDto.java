package com.ecommerce.modules.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentTrackingDto {
    private Long id;
    private String status;
    private String location;
    private String statusDescription;
    private Instant recordedAt;
}

