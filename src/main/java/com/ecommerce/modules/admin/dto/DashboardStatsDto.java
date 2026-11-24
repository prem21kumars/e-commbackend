package com.ecommerce.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    private BigDecimal totalRevenue;
    private long totalOrders;
    private long pendingOrders;
    private long confirmedOrders;
    private long deliveredOrders;
    private long totalCustomers;
    private long totalProducts;
    private long lowStockProductsCount;
}

