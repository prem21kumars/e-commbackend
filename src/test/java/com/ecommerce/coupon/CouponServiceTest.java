package com.ecommerce.coupon;

import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.coupon.dto.CouponValidationResultDto;
import com.ecommerce.modules.coupon.entity.Coupon;
import com.ecommerce.modules.coupon.entity.DiscountType;
import com.ecommerce.modules.coupon.repository.CouponRepository;
import com.ecommerce.modules.coupon.repository.CouponUsageRepository;
import com.ecommerce.modules.coupon.service.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService Unit Tests")
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponUsageRepository usageRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CouponService couponService;

    private Coupon percentageCoupon;

    @BeforeEach
    void setUp() {
        percentageCoupon = Coupon.builder()
                .id(1L)
                .code("WELCOME20")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00")) // 20%
                .minOrderAmount(new BigDecimal("500.00"))
                .maxDiscountAmount(new BigDecimal("200.00")) // Cap at 200
                .usageLimit(100)
                .perUserLimit(1)
                .timesUsed(0)
                .active(true)
                .startDate(Instant.now().minusSeconds(3600))
                .endDate(Instant.now().plusSeconds(86400))
                .build();
    }

    @Test
    @DisplayName("Validates coupon with percentage discount capped at maxDiscountAmount")
    void testValidateCouponWithMaxCap() {
        when(couponRepository.findByCodeIgnoreCaseAndActiveTrue("WELCOME20"))
                .thenReturn(Optional.of(percentageCoupon));
        when(usageRepository.countByCouponIdAndUserId(1L, 10L)).thenReturn(0L);

        // Subtotal = 2000. 20% of 2000 is 400. But max is 200.
        CouponValidationResultDto result = couponService.validateCoupon(10L, "WELCOME20", new BigDecimal("2000.00"));

        assertTrue(result.isValid());
        assertEquals("WELCOME20", result.getCode());
        assertEquals(new BigDecimal("200.00"), result.getDiscountAmount());
    }

    @Test
    @DisplayName("Rejects coupon when subtotal is below minOrderAmount")
    void testRejectCouponBelowMinimum() {
        when(couponRepository.findByCodeIgnoreCaseAndActiveTrue("WELCOME20"))
                .thenReturn(Optional.of(percentageCoupon));

        // Subtotal = 400 (min is 500)
        CouponValidationResultDto result = couponService.validateCoupon(10L, "WELCOME20", new BigDecimal("400.00"));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("Minimum order amount"));
    }

    @Test
    @DisplayName("Rejects coupon when user has already reached perUserLimit")
    void testRejectCouponPerUserLimitExceeded() {
        when(couponRepository.findByCodeIgnoreCaseAndActiveTrue("WELCOME20"))
                .thenReturn(Optional.of(percentageCoupon));
        when(usageRepository.countByCouponIdAndUserId(1L, 10L)).thenReturn(1L);

        CouponValidationResultDto result = couponService.validateCoupon(10L, "WELCOME20", new BigDecimal("1000.00"));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("maximum allowed times"));
    }
}

