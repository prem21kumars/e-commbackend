package com.ecommerce.modules.coupon.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.coupon.dto.CouponDto;
import com.ecommerce.modules.coupon.dto.CouponValidationResultDto;
import com.ecommerce.modules.coupon.dto.CreateCouponRequest;
import com.ecommerce.modules.coupon.entity.Coupon;
import com.ecommerce.modules.coupon.entity.CouponUsage;
import com.ecommerce.modules.coupon.repository.CouponRepository;
import com.ecommerce.modules.coupon.repository.CouponUsageRepository;
import com.ecommerce.modules.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CouponValidationResultDto validateCoupon(Long userId, String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return CouponValidationResultDto.builder().valid(false).message("Coupon code cannot be empty").build();
        }

        Optional<Coupon> couponOpt = couponRepository.findByCodeIgnoreCaseAndActiveTrue(code.trim());
        if (couponOpt.isEmpty()) {
            return CouponValidationResultDto.builder().valid(false).code(code).message("Invalid or inactive coupon code").build();
        }

        Coupon coupon = couponOpt.get();

        if (coupon.isExpired()) {
            return CouponValidationResultDto.builder().valid(false).code(code).message("Coupon has expired").build();
        }

        if (coupon.isExhausted()) {
            return CouponValidationResultDto.builder().valid(false).code(code).message("Coupon usage limit has been reached").build();
        }

        if (subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            return CouponValidationResultDto.builder()
                    .valid(false)
                    .code(code)
                    .message("Minimum order amount of INR " + coupon.getMinOrderAmount() + " required to use this coupon")
                    .build();
        }

        if (userId != null) {
            long usedCount = usageRepository.countByCouponIdAndUserId(coupon.getId(), userId);
            if (usedCount >= coupon.getPerUserLimit()) {
                return CouponValidationResultDto.builder()
                        .valid(false)
                        .code(code)
                        .message("You have already used this coupon the maximum allowed times (" + coupon.getPerUserLimit() + ")")
                        .build();
            }
        }

        BigDecimal discount = coupon.calculateDiscount(subtotal);

        return CouponValidationResultDto.builder()
                .valid(true)
                .code(coupon.getCode())
                .discountAmount(discount)
                .message("Coupon applied successfully! You save INR " + discount)
                .build();
    }

    @Transactional
    public BigDecimal applyCoupon(Long userId, String code, BigDecimal subtotal, Order order) {
        CouponValidationResultDto validation = validateCoupon(userId, code, subtotal);
        if (!validation.isValid()) {
            throw new BadRequestException("Coupon error: " + validation.getMessage());
        }

        Coupon coupon = couponRepository.findByCodeIgnoreCaseAndActiveTrue(code.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "code", code));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        coupon.setTimesUsed(coupon.getTimesUsed() + 1);
        couponRepository.save(coupon);

        CouponUsage usage = CouponUsage.builder()
                .coupon(coupon)
                .user(user)
                .order(order)
                .discountApplied(validation.getDiscountAmount())
                .usedAt(Instant.now())
                .build();
        usageRepository.save(usage);

        return validation.getDiscountAmount();
    }

    @Transactional
    public CouponDto createCoupon(CreateCouponRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Coupon code '" + code + "' already exists");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("Coupon end date cannot be earlier than start date");
        }

        Coupon coupon = Coupon.builder()
                .code(code)
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .minOrderAmount(request.getMinOrderAmount())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .usageLimit(request.getUsageLimit())
                .perUserLimit(request.getPerUserLimit())
                .timesUsed(0)
                .active(true)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        Coupon saved = couponRepository.save(coupon);
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<CouponDto> getAllCoupons() {
        return couponRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private CouponDto mapToDto(Coupon c) {
        return CouponDto.builder()
                .id(c.getId())
                .code(c.getCode())
                .discountType(c.getDiscountType())
                .discountValue(c.getDiscountValue())
                .minOrderAmount(c.getMinOrderAmount())
                .maxDiscountAmount(c.getMaxDiscountAmount())
                .usageLimit(c.getUsageLimit())
                .perUserLimit(c.getPerUserLimit())
                .timesUsed(c.getTimesUsed())
                .active(c.isActive())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .build();
    }
}

