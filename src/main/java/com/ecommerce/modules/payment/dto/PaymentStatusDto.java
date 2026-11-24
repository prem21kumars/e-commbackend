package com.ecommerce.modules.payment.dto;

import com.ecommerce.modules.payment.entity.PaymentStatus;
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
public class PaymentStatusDto {
    private String orderNumber;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private Instant paidAt;
    private String message;
}

