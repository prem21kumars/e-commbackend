package com.ecommerce.modules.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidationResultDto {
    private boolean valid;
    private String code;
    private BigDecimal discountAmount;
    private String message;
}

