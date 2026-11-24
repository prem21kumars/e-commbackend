package com.ecommerce.modules.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundDto {
    private Long id;
    private Long orderId;
    private String orderNumber;
    private BigDecimal refundAmount;
    private String status;
    private String gatewayRefundId;
    private String reason;
    private Instant processedAt;
    private String createdBy;
}

