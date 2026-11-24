package com.ecommerce.modules.coupon.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.modules.coupon.dto.CouponDto;
import com.ecommerce.modules.coupon.dto.CouponValidationResultDto;
import com.ecommerce.modules.coupon.dto.CreateCouponRequest;
import com.ecommerce.modules.coupon.dto.ValidateCouponRequest;
import com.ecommerce.modules.coupon.service.CouponService;
import com.ecommerce.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Coupons & Discounts", description = "Endpoints for coupon verification, discount application, and coupon management")
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/coupons/validate")
    @Operation(summary = "Validate Coupon", description = "Validates promo code against order subtotal and customer usage limits")
    public ResponseEntity<ApiResponse<CouponValidationResultDto>> validateCoupon(
            @Valid @RequestBody ValidateCouponRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal != null ? principal.getId() : null;
        CouponValidationResultDto result = couponService.validateCoupon(userId, request.getCode(), request.getSubtotal());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/admin/coupons")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List All Coupons (Admin)", description = "Retrieves all active and expired coupons with usage statistics")
    public ResponseEntity<ApiResponse<List<CouponDto>>> getAllCoupons() {
        List<CouponDto> coupons = couponService.getAllCoupons();
        return ResponseEntity.ok(ApiResponse.success(coupons));
    }

    @PostMapping("/admin/coupons")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create Coupon (Admin)", description = "Creates a new percentage or fixed discount coupon")
    public ResponseEntity<ApiResponse<CouponDto>> createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        CouponDto created = couponService.createCoupon(request);
        return new ResponseEntity<>(ApiResponse.success("Coupon created successfully", created), HttpStatus.CREATED);
    }
}

