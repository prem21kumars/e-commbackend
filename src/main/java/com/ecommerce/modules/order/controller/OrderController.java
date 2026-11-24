package com.ecommerce.modules.order.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PagedResponse;
import com.ecommerce.modules.order.dto.*;
import com.ecommerce.modules.order.service.OrderService;
import com.ecommerce.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "Endpoints for order creation, customer order history, tracking, and cancellations")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create Order", description = "Converts shopping cart into an order with stock reservations, discount calculations, and payment initialization")
    public ResponseEntity<ApiResponse<OrderDetailDto>> createOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderDetailDto order = orderService.createOrder(principal.getId(), request);
        return new ResponseEntity<>(ApiResponse.success("Order created successfully", order), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get Customer Order History", description = "Paginated list of all previous orders placed by the customer")
    public ResponseEntity<ApiResponse<PagedResponse<OrderSummaryDto>>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PagedResponse<OrderSummaryDto> orders = orderService.getMyOrders(principal.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/{orderNumber}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get Order Details", description = "Retrieves full line items, snapshots, and shipping info for an order")
    public ResponseEntity<ApiResponse<OrderDetailDto>> getOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNumber
    ) {
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        OrderDetailDto order = orderService.getOrderByNumber(principal.getId(), orderNumber, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PostMapping("/{orderNumber}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel Order", description = "Cancels an unfulfilled order and releases inventory holds")
    public ResponseEntity<ApiResponse<OrderDetailDto>> cancelOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String orderNumber,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        OrderDetailDto order = orderService.cancelOrder(principal.getId(), orderNumber, request.getReason(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", order));
    }
}

