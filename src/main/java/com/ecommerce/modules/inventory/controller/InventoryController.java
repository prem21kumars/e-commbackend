package com.ecommerce.modules.inventory.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PagedResponse;
import com.ecommerce.modules.inventory.dto.*;
import com.ecommerce.modules.inventory.service.InventoryService;
import com.ecommerce.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Inventory Management", description = "Endpoints for stock checks, inventory adjustments, and movement history")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/products/{id}/availability")
    @Operation(summary = "Check Stock Availability", description = "Public probe to verify if a product is in stock and available to sell")
    public ResponseEntity<ApiResponse<StockAvailabilityDto>> checkAvailability(@PathVariable Long id) {
        StockAvailabilityDto availability = inventoryService.checkAvailability(id);
        return ResponseEntity.ok(ApiResponse.success(availability));
    }

    @GetMapping("/admin/inventory/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Get Product Stock Status (Admin)", description = "Retrieves available, reserved, and threshold stock levels")
    public ResponseEntity<ApiResponse<InventoryDto>> getInventory(@PathVariable Long productId) {
        InventoryDto inventory = inventoryService.getInventoryByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success(inventory));
    }

    @PutMapping("/admin/inventory/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Adjust Stock Level (Admin)", description = "Adjust stock quantity atomically with audit logging")
    public ResponseEntity<ApiResponse<InventoryDto>> adjustStock(
            @PathVariable Long productId,
            @Valid @RequestBody AdjustStockRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String adminUser = principal != null ? principal.getEmail() : "ADMIN";
        InventoryDto updated = inventoryService.adjustStock(productId, request, adminUser);
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted successfully", updated));
    }

    @GetMapping("/admin/inventory/{productId}/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Stock Movement History (Admin)", description = "Audit trail of all restocks, reservations, and deductions")
    public ResponseEntity<ApiResponse<PagedResponse<InventoryMovementDto>>> getMovements(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<InventoryMovementDto> movements = inventoryService.getMovements(productId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(movements)));
    }

    @GetMapping("/admin/inventory/alerts/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Low-Stock Alerts (Admin)", description = "Lists all products whose available stock is at or below threshold")
    public ResponseEntity<ApiResponse<PagedResponse<InventoryDto>>> getLowStockAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<InventoryDto> alerts = inventoryService.getLowStockAlerts(pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(alerts)));
    }
}

