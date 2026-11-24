package com.ecommerce.modules.admin.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PagedResponse;
import com.ecommerce.modules.admin.dto.AuditLogDto;
import com.ecommerce.modules.admin.dto.DashboardStatsDto;
import com.ecommerce.modules.admin.dto.SalesReportDto;
import com.ecommerce.modules.admin.dto.TopSellingProductDto;
import com.ecommerce.modules.admin.service.AdminReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Admin Analytics & Reporting", description = "Endpoints for executive reporting, sales analytics, top products, and security audit logs")
public class AdminDashboardController {

    private final AdminReportService reportService;

    @GetMapping("/dashboard")
    @Operation(summary = "Admin Dashboard Overview", description = "High-level summary metrics: total revenue, order statuses, customer count, and low stock warnings")
    public ResponseEntity<ApiResponse<DashboardStatsDto>> getDashboardStats() {
        DashboardStatsDto stats = reportService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/reports/sales")
    @Operation(summary = "Daily Sales Report", description = "Time-series daily breakdown of order counts, revenue, and average order value")
    public ResponseEntity<ApiResponse<List<SalesReportDto>>> getSalesReport(
            @RequestParam(defaultValue = "14") int pastDays
    ) {
        List<SalesReportDto> sales = reportService.getDailySalesReport(pastDays);
        return ResponseEntity.ok(ApiResponse.success(sales));
    }

    @GetMapping("/reports/top-products")
    @Operation(summary = "Top Selling Products", description = "Ranks products by total units sold and revenue contribution")
    public ResponseEntity<ApiResponse<List<TopSellingProductDto>>> getTopProducts(
            @RequestParam(defaultValue = "5") int limit
    ) {
        List<TopSellingProductDto> top = reportService.getTopSellingProducts(limit);
        return ResponseEntity.ok(ApiResponse.success(top));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Security Audit Logs", description = "Paginated system activity log with correlation IDs and entity mutation history")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<AuditLogDto> logs = reportService.getAuditLogs(pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}

