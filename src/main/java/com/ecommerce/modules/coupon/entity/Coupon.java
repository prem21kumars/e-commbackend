package com.ecommerce.modules.coupon.entity;

import com.ecommerce.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "per_user_limit", nullable = false)
    @Builder.Default
    private int perUserLimit = 1;

    @Column(name = "times_used", nullable = false)
    @Builder.Default
    private int timesUsed = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    public boolean isExpired() {
        Instant now = Instant.now();
        return now.isBefore(startDate) || now.isAfter(endDate);
    }

    public boolean isExhausted() {
        return usageLimit != null && timesUsed >= usageLimit;
    }

    public BigDecimal calculateDiscount(BigDecimal orderSubtotal) {
        if (!active || isExpired() || isExhausted()) {
            return BigDecimal.ZERO;
        }

        if (orderSubtotal.compareTo(minOrderAmount) < 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal calculatedDiscount;
        if (discountType == DiscountType.PERCENTAGE) {
            calculatedDiscount = orderSubtotal.multiply(discountValue).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            if (maxDiscountAmount != null && calculatedDiscount.compareTo(maxDiscountAmount) > 0) {
                calculatedDiscount = maxDiscountAmount;
            }
        } else {
            calculatedDiscount = discountValue;
        }

        return calculatedDiscount.min(orderSubtotal);
    }
}

