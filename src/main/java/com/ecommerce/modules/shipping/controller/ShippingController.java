package com.ecommerce.modules.shipping.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.modules.shipping.dto.*;
import com.ecommerce.modules.shipping.service.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Shipping & Logistics", description = "Endpoints for order fulfillment, shipment tracking, and carrier delivery updates")
public class ShippingController {

    private final ShippingService shippingService;

    @GetMapping("/shipping/orders/{orderNumber}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get Shipment for Order", description = "Retrieves tracking details and status history for a customer order")
    public ResponseEntity<ApiResponse<ShipmentDto>> getShipmentByOrder(@PathVariable String orderNumber) {
        ShipmentDto shipment = shippingService.getShipmentByOrderNumber(orderNumber);
        return ResponseEntity.ok(ApiResponse.success(shipment));
    }

    @GetMapping("/shipping/track/{trackingNumber}")
    @Operation(summary = "Track by Tracking Number", description = "Public shipment tracking probe")
    public ResponseEntity<ApiResponse<ShipmentDto>> trackShipment(@PathVariable String trackingNumber) {
        ShipmentDto shipment = shippingService.getShipmentByTrackingNumber(trackingNumber);
        return ResponseEntity.ok(ApiResponse.success(shipment));
    }

    @PostMapping("/admin/shipping/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create Shipment (Admin)", description = "Assigns carrier and tracking number to fulfill a confirmed order")
    public ResponseEntity<ApiResponse<ShipmentDto>> createShipment(
            @PathVariable Long orderId,
            @Valid @RequestBody CreateShipmentRequest request
    ) {
        ShipmentDto shipment = shippingService.createShipment(orderId, request);
        return new ResponseEntity<>(ApiResponse.success("Shipment created successfully", shipment), HttpStatus.CREATED);
    }

    @PutMapping("/admin/shipping/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update Delivery Status (Admin)", description = "Updates logistics progress (e.g. IN_TRANSIT, DELIVERED) and adds tracking checkpoint")
    public ResponseEntity<ApiResponse<ShipmentDto>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShipmentStatusRequest request
    ) {
        ShipmentDto shipment = shippingService.updateShipmentStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Shipment status updated", shipment));
    }
}

