package com.ecommerce.modules.order.dto;

import com.ecommerce.modules.order.entity.OrderStatus;
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
public class OrderSummaryDto {
    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private int itemCount;
    private BigDecimal totalAmount;
    private String currency;
    private Instant createdAt;
}

