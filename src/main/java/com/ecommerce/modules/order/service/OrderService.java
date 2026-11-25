    package com.ecommerce.modules.order.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ForbiddenException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.response.PagedResponse;
import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.cart.entity.Cart;
import com.ecommerce.modules.cart.entity.CartItem;
import com.ecommerce.modules.cart.repository.CartItemRepository;
import com.ecommerce.modules.cart.repository.CartRepository;
import com.ecommerce.modules.coupon.service.CouponService;
import com.ecommerce.modules.customer.entity.CustomerAddress;
import com.ecommerce.modules.customer.repository.CustomerAddressRepository;
import com.ecommerce.modules.inventory.service.InventoryService;
import com.ecommerce.modules.order.dto.*;
import com.ecommerce.modules.order.entity.Order;
import com.ecommerce.modules.order.entity.OrderItem;
import com.ecommerce.modules.order.entity.OrderStatus;
import com.ecommerce.modules.order.repository.OrderItemRepository;
import com.ecommerce.modules.order.repository.OrderRepository;
import com.ecommerce.modules.payment.entity.Payment;
import com.ecommerce.modules.payment.entity.PaymentMethod;
import com.ecommerce.modules.payment.entity.PaymentStatus;
import com.ecommerce.modules.payment.repository.PaymentRepository;
import com.ecommerce.modules.product.entity.Product;
import com.ecommerce.modules.product.entity.ProductImage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final CustomerAddressRepository addressRepository;
    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public OrderDetailDto createOrder(Long userId, CreateOrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Cart cart = cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new BadRequestException("Shopping cart is empty"));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot create an order from an empty shopping cart");
        }

        CustomerAddress shippingAddress = addressRepository.findByIdAndUserId(request.getShippingAddressId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipping Address", "id", request.getShippingAddressId()));

        CustomerAddress billingAddress = shippingAddress;
        if (request.getBillingAddressId() != null) {
            billingAddress = addressRepository.findByIdAndUserId(request.getBillingAddressId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Billing Address", "id", request.getBillingAddressId()));
        }

        String shippingSnapshot;
        String billingSnapshot;
        try {
            shippingSnapshot = objectMapper.writeValueAsString(shippingAddress);
            billingSnapshot = objectMapper.writeValueAsString(billingAddress);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Failed to serialize address snapshots: " + e.getMessage());
        }

        // Validate stock and reserve items atomically
        List<String> reservationTokens = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            int qty = cartItem.getQuantity();

            // Reserve stock via pessimistic lock
            String token = inventoryService.reserveStock(product.getId(), qty, null);
            reservationTokens.add(token);

            BigDecimal currentPrice = product.getEffectivePrice();
            BigDecimal itemTotal = currentPrice.multiply(BigDecimal.valueOf(qty));
            subtotal = subtotal.add(itemTotal);

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .sku(product.getSku())
                    .unitPrice(currentPrice)
                    .quantity(qty)
                    .totalPrice(itemTotal)
                    .build();
            orderItems.add(orderItem);
        }

        // Calculate discounts
        BigDecimal discountAmount = BigDecimal.ZERO;
        String couponCode = null;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            couponCode = request.getCouponCode().trim().toUpperCase();
        }

        // Standard shipping rules: Free over INR 999, else INR 99
        BigDecimal shippingFee = (subtotal.compareTo(new BigDecimal("999.00")) >= 0)
                ? BigDecimal.ZERO
                : new BigDecimal("99.00");

        // Tax calculation: 5% included GST
        BigDecimal taxAmount = subtotal.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalAmount = subtotal.subtract(discountAmount).add(shippingFee);

        String orderNumber = generateOrderNumber();

        boolean isCod = request.getPaymentMethod() != null &&
                (request.getPaymentMethod().toUpperCase().contains("CASH") ||
                 request.getPaymentMethod().toUpperCase().contains("COD"));

        OrderStatus initialStatus = isCod ? OrderStatus.CONFIRMED : OrderStatus.PAYMENT_PENDING;

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .user(user)
                .status(initialStatus)
                .subtotal(subtotal)
                .taxAmount(taxAmount)
                .shippingFee(shippingFee)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .currency("INR")
                .couponCode(couponCode)
                .shippingAddressSnapshot(shippingSnapshot)
                .billingAddressSnapshot(billingSnapshot)
                .build();

        Order savedOrder = orderRepository.save(order);

        // Apply coupon usage record if applicable
        if (couponCode != null) {
            try {
                discountAmount = couponService.applyCoupon(userId, couponCode, subtotal, savedOrder);
                savedOrder.setDiscountAmount(discountAmount);
                savedOrder.setTotalAmount(subtotal.subtract(discountAmount).add(shippingFee));
                savedOrder = orderRepository.save(savedOrder);
            } catch (Exception e) {
                log.warn("Coupon application failed for order {}: {}", orderNumber, e.getMessage());
            }
        }

        for (OrderItem item : orderItems) {
            item.setOrder(savedOrder);
        }
        orderItemRepository.saveAll(orderItems);
        savedOrder.setItems(orderItems);

        // Record payment transaction
        PaymentMethod pm = isCod ? PaymentMethod.CASH_ON_DELIVERY : PaymentMethod.RAZORPAY;
        Payment payment = Payment.builder()
                .order(savedOrder)
                .paymentMethod(pm)
                .status(PaymentStatus.PENDING)
                .amount(savedOrder.getTotalAmount())
                .currency("INR")
                .gatewayOrderId(isCod ? ("COD-" + orderNumber) : ("sim_" + orderNumber))
                .build();
        paymentRepository.save(payment);
        savedOrder.getPayments().add(payment);

        // Clear the user's cart
        cartItemRepository.deleteByCartId(cart.getId());
        cart.getItems().clear();

        log.info("Order created successfully: orderNumber={}, total={}, userId={}", orderNumber, totalAmount, userId);
        return mapToDetailDto(savedOrder);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderSummaryDto> getMyOrders(Long userId, Pageable pageable) {
        Page<Order> page = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        List<OrderSummaryDto> summaries = page.getContent().stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());

        return PagedResponse.<OrderSummaryDto>builder()
                .content(summaries)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public OrderDetailDto getOrderByNumber(Long userId, String orderNumber, boolean isAdmin) {
        Order order = orderRepository.findByOrderNumberWithItems(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        if (!isAdmin && !order.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to view this order");
        }

        return mapToDetailDto(order);
    }

    @Transactional
    public OrderDetailDto cancelOrder(Long userId, String orderNumber, String reason, boolean isAdmin) {
        Order order = orderRepository.findByOrderNumberWithItems(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        if (!isAdmin && !order.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to cancel this order");
        }

        if (!order.canBeCancelled()) {
            throw new BadRequestException("Order cannot be cancelled in its current state: " + order.getStatus());
        }

        // 1-day (24 hours) cancellation policy: Customers can only cancel within 24 hours of placement.
        // After 24 hours, only an administrator can cancel or modify this order.
        if (!isAdmin && order.getCreatedAt() != null) {
            java.time.Instant oneDayAgo = java.time.Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS);
            if (order.getCreatedAt().isBefore(oneDayAgo)) {
                throw new BadRequestException("Order cancellation window has closed (available within 24 hours of placement). Only an administrator can cancel this order.");
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        Order updated = orderRepository.save(order);

        log.info("Order {} cancelled: reason='{}'", orderNumber, reason);
        return mapToDetailDto(updated);
    }

    @Transactional
    public void confirmOrderPayment(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("Order {} confirmed upon successful payment verification", orderNumber);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AdminOrderDto> getAllOrdersForAdmin(OrderStatus status, Pageable pageable) {
        Page<Order> page = (status != null)
                ? orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : orderRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<AdminOrderDto> dtos = page.getContent().stream()
                .map(this::mapToAdminOrderDto)
                .collect(Collectors.toList());

        return PagedResponse.<AdminOrderDto>builder()
                .content(dtos)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional
    public OrderDetailDto updateOrderStatusByAdmin(Long orderId, OrderStatus newStatus, String notes) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        if (notes != null && !notes.isBlank() && newStatus == OrderStatus.CANCELLED) {
            order.setCancellationReason(notes);
        }
        Order saved = orderRepository.save(order);
        log.info("Admin updated order ID {} status from {} to {}. Notes: {}", orderId, oldStatus, newStatus, notes);
        return mapToDetailDto(saved);
    }

    private AdminOrderDto mapToAdminOrderDto(Order order) {
        User u = order.getUser();
        String customerName = u != null ? (u.getFirstName() + " " + u.getLastName()).trim() : "Guest";
        String customerEmail = u != null ? u.getEmail() : "";
        String customerPhone = u != null ? u.getPhoneNumber() : "";

        String paymentMethod = "CASH_ON_DELIVERY";
        String paymentStatus = "PENDING";
        if (order.getPayments() != null && !order.getPayments().isEmpty()) {
            Payment p = order.getPayments().getFirst();
            paymentMethod = p.getPaymentMethod() != null ? p.getPaymentMethod().name() : "CASH_ON_DELIVERY";
            paymentStatus = p.getStatus() != null ? p.getStatus().name() : "PENDING";
        }

        return AdminOrderDto.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .currency(order.getCurrency())
                .userId(u != null ? u.getId() : null)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .customerPhone(customerPhone)
                .itemCount(order.getItems() != null ? order.getItems().size() : 0)
                .shippingAddressSnapshot(order.getShippingAddressSnapshot())
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private String generateOrderNumber() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int randomCode = 100000 + secureRandom.nextInt(900000);
        return "ORD-" + datePrefix + "-" + randomCode;
    }

    private OrderSummaryDto mapToSummaryDto(Order order) {
        return OrderSummaryDto.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .itemCount(order.getItems() != null ? order.getItems().size() : 0)
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderDetailDto mapToDetailDto(Order order) {
        List<OrderItemDto> itemDtos = (order.getItems() != null)
                ? order.getItems().stream().map(item -> {
            Product p = item.getProduct();
            String primaryImg = (p != null && p.getImages() != null && !p.getImages().isEmpty())
                    ? p.getImages().stream().filter(ProductImage::isPrimary).findFirst().orElse(p.getImages().getFirst()).getImageUrl()
                    : null;

            return OrderItemDto.builder()
                    .id(item.getId())
                    .productId(p != null ? p.getId() : null)
                    .productName(item.getProductName())
                    .productSku(item.getSku())
                    .imageUrl(primaryImg)
                    .unitPrice(item.getUnitPrice())
                    .quantity(item.getQuantity())
                    .totalPrice(item.getTotalPrice())
                    .build();
        }).collect(Collectors.toList())
                : new ArrayList<>();

        String razorpayOrderId = (order.getPayments() != null && !order.getPayments().isEmpty())
                ? order.getPayments().getFirst().getGatewayOrderId()
                : null;

        String paymentMethod = "CASH_ON_DELIVERY";
        String paymentStatus = "PENDING";
        if (order.getPayments() != null && !order.getPayments().isEmpty()) {
            Payment p = order.getPayments().getFirst();
            paymentMethod = p.getPaymentMethod() != null ? p.getPaymentMethod().name() : "CASH_ON_DELIVERY";
            paymentStatus = p.getStatus() != null ? p.getStatus().name() : "PENDING";
        }

        return OrderDetailDto.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUser().getId())
                .userEmail(order.getUser().getEmail())
                .status(order.getStatus())
                .subtotal(order.getSubtotal())
                .taxAmount(order.getTaxAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .couponCode(order.getCouponCode())
                .shippingAddressJson(order.getShippingAddressSnapshot())
                .billingAddressJson(order.getBillingAddressSnapshot())
                .cancellationReason(order.getCancellationReason())
                .canBeCancelled(order.canBeCancelled())
                .createdAt(order.getCreatedAt())
                .items(itemDtos)
                .razorpayOrderId(razorpayOrderId)
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .build();
    }
}

