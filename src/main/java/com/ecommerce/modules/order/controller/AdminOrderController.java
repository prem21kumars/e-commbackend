package com.ecommerce.modules.order.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PagedResponse;
import com.ecommerce.modules.order.dto.AdminOrderDto;
import com.ecommerce.modules.order.dto.OrderDetailDto;
import com.ecommerce.modules.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.modules.order.entity.OrderStatus;
import com.ecommerce.modules.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Order Management", description = "Endpoints for administrator oversight, queue monitoring, and fulfillment status updates")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "List All Orders (Admin)", description = "Paginated overview of all orders across customers with optional status filter")
    public ResponseEntity<ApiResponse<PagedResponse<AdminOrderDto>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PagedResponse<AdminOrderDto> orders = orderService.getAllOrdersForAdmin(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update Order Fulfillment Status", description = "Updates order progress to Processing, Shipped, Delivered, or Cancelled")
    public ResponseEntity<ApiResponse<OrderDetailDto>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        OrderDetailDto updated = orderService.updateOrderStatusByAdmin(id, request.getStatus(), request.getNotes());
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", updated));
    }
}

