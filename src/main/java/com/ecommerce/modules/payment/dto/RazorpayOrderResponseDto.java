package com.ecommerce.modules.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayOrderResponseDto {
    private String razorpayOrderId;
    private String orderNumber;
    private BigDecimal amount;
    private long amountInPaise;
    private String currency;
    private String razorpayKeyId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
}

