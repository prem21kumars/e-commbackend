package com.ecommerce.modules.admin.service;

import com.ecommerce.common.response.PagedResponse;
import com.ecommerce.modules.admin.dto.*;
import com.ecommerce.modules.admin.entity.AuditLog;
import com.ecommerce.modules.admin.repository.AuditLogRepository;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.inventory.repository.InventoryRepository;
import com.ecommerce.modules.order.entity.Order;
import com.ecommerce.modules.order.entity.OrderItem;
import com.ecommerce.modules.order.entity.OrderStatus;
import com.ecommerce.modules.order.repository.OrderRepository;
import com.ecommerce.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        List<Order> allOrders = orderRepository.findAll();

        BigDecimal totalRevenue = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CONFIRMED ||
                             o.getStatus() == OrderStatus.PROCESSING ||
                             o.getStatus() == OrderStatus.SHIPPED ||
                             o.getStatus() == OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalOrders = allOrders.size();
        long pendingOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PAYMENT_PENDING || o.getStatus() == OrderStatus.PENDING).count();
        long confirmedOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.CONFIRMED).count();
        long deliveredOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();

        long totalCustomers = userRepository.count();
        long totalProducts = productRepository.count();
        long lowStockCount = inventoryRepository.findLowStockInventories(PageRequest.of(0, 1)).getTotalElements();

        return DashboardStatsDto.builder()
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .pendingOrders(pendingOrders)
                .confirmedOrders(confirmedOrders)
                .deliveredOrders(deliveredOrders)
                .totalCustomers(totalCustomers)
                .totalProducts(totalProducts)
                .lowStockProductsCount(lowStockCount)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SalesReportDto> getDailySalesReport(int pastDays) {
        LocalDate startDate = LocalDate.now().minusDays(pastDays);
        List<Order> orders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED && o.getStatus() != OrderStatus.PENDING)
                .collect(Collectors.toList());

        Map<LocalDate, List<Order>> ordersByDate = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate()));

        List<SalesReportDto> report = new ArrayList<>();
        for (int i = pastDays; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            List<Order> dayOrders = ordersByDate.getOrDefault(date, Collections.emptyList());

            long count = dayOrders.size();
            BigDecimal revenue = dayOrders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = (count > 0) ? revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            report.add(SalesReportDto.builder()
                    .date(date)
                    .orderCount(count)
                    .revenue(revenue)
                    .averageOrderValue(avg)
                    .build());
        }

        return report;
    }

    @Transactional(readOnly = true)
    public List<TopSellingProductDto> getTopSellingProducts(int limit) {
        List<Order> validOrders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.CONFIRMED || o.getStatus() == OrderStatus.DELIVERED)
                .collect(Collectors.toList());

        Map<Long, List<OrderItem>> itemsByProduct = validOrders.stream()
                .flatMap(o -> o.getItems().stream())
                .collect(Collectors.groupingBy(i -> i.getProduct().getId()));

        return itemsByProduct.entrySet().stream()
                .map(entry -> {
                    List<OrderItem> items = entry.getValue();
                    long unitsSold = items.stream().mapToLong(OrderItem::getQuantity).sum();
                    BigDecimal totalRev = items.stream().map(OrderItem::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
                    OrderItem sample = items.getFirst();

                    return TopSellingProductDto.builder()
                            .productId(entry.getKey())
                            .productName(sample.getProductName())
                            .sku(sample.getSku())
                            .unitsSold(unitsSold)
                            .totalRevenue(totalRev)
                            .build();
                })
                .sorted(Comparator.comparingLong(TopSellingProductDto::getUnitsSold).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogDto> getAuditLogs(Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findAllByOrderByTimestampDesc(pageable);
        return PagedResponse.from(page.map(a -> AuditLogDto.builder()
                .id(a.getId())
                .entityName(a.getEntityName())
                .entityId(a.getEntityId())
                .action(a.getAction())
                .performedBy(a.getPerformedBy())
                .ipAddress(a.getIpAddress())
                .oldState(a.getOldState())
                .newState(a.getNewState())
                .correlationId(a.getCorrelationId())
                .timestamp(a.getTimestamp())
                .build()));
    }
}

