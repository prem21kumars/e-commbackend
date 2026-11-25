package com.ecommerce.modules.order.dto;

import com.ecommerce.modules.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailDto {
    private Long id;
    private String orderNumber;
    private Long userId;
    private String userEmail;
    private OrderStatus status;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String currency;
    private String couponCode;
    private String shippingAddressJson;
    private String billingAddressJson;
    private String cancellationReason;
    private boolean canBeCancelled;
    private Instant createdAt;
    private List<OrderItemDto> items;
    private String razorpayOrderId;
    private String paymentMethod;
    private String paymentStatus;
}

