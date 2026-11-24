package com.ecommerce.modules.payment.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.modules.payment.dto.*;
import com.ecommerce.modules.payment.service.RazorpayService;
import com.ecommerce.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Payments — Razorpay", description = "Endpoints for Razorpay checkout initiation, signature verification, webhooks, and refunds")
public class PaymentController {

    private final RazorpayService razorpayService;

    @PostMapping("/payments/razorpay/order")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create Razorpay Order", description = "Initiates server-side Razorpay order and returns gateway transaction parameters")
    public ResponseEntity<ApiResponse<RazorpayOrderResponseDto>> createRazorpayOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRazorpayOrderRequest request
    ) {
        RazorpayOrderResponseDto response = razorpayService.createRazorpayOrder(request.getOrderNumber(), principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/payments/razorpay/verify")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Verify Payment Signature", description = "Cryptographically verifies Razorpay payment signature and confirms order")
    public ResponseEntity<ApiResponse<PaymentStatusDto>> verifyPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VerifyPaymentRequest request
    ) {
        PaymentStatusDto status = razorpayService.verifyPayment(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/payments/razorpay/webhook")
    @Operation(summary = "Razorpay Webhook Handler", description = "Processes asynchronous webhook events with raw payload signature validation and idempotency")
    public ResponseEntity<ApiResponse<String>> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) {
        razorpayService.processWebhook(rawPayload, signature);
        return ResponseEntity.ok(ApiResponse.success("Webhook event accepted"));
    }

    @PostMapping("/admin/payments/refund/{orderNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Process Refund (Admin)", description = "Initiates payment refund for returned or cancelled orders")
    public ResponseEntity<ApiResponse<RefundDto>> processRefund(
            @PathVariable String orderNumber,
            @Valid @RequestBody ProcessRefundRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String adminUser = principal != null ? principal.getEmail() : "ADMIN";
        RefundDto refund = razorpayService.processRefund(orderNumber, request, adminUser);
        return ResponseEntity.ok(ApiResponse.success("Refund processed successfully", refund));
    }
}

