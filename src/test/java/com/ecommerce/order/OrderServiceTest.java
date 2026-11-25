package com.ecommerce.order;

import com.ecommerce.common.exception.BadRequestException;
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
import com.ecommerce.modules.order.dto.CancelOrderRequest;
import com.ecommerce.modules.order.dto.CreateOrderRequest;
import com.ecommerce.modules.order.dto.OrderDetailDto;
import com.ecommerce.modules.order.entity.Order;
import com.ecommerce.modules.order.entity.OrderStatus;
import com.ecommerce.modules.order.repository.OrderItemRepository;
import com.ecommerce.modules.order.repository.OrderRepository;
import com.ecommerce.modules.order.service.OrderService;
import com.ecommerce.modules.product.entity.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecommerce.modules.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomerAddressRepository addressRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private CouponService couponService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private CustomerAddress address;
    private Cart cart;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("shopper@example.com").firstName("John").lastName("Doe").build();
        address = CustomerAddress.builder().id(10L).user(user).recipientName("John").phoneNumber("9876543210").city("Bengaluru").build();

        product = Product.builder()
                .id(100L)
                .name("Wooden Toy Train")
                .sku("TOY-TRN-01")
                .basePrice(new BigDecimal("1500.00"))
                .images(new ArrayList<>())
                .build();

        CartItem item = CartItem.builder().id(50L).product(product).quantity(2).unitPrice(new BigDecimal("1500.00")).build();
        cart = Cart.builder().id(5L).user(user).items(new ArrayList<>(List.of(item))).build();
    }

    @Test
    @DisplayName("Create order reserves inventory, calculates total, and clears cart")
    void testCreateOrderSuccess() throws Exception {
        CreateOrderRequest req = CreateOrderRequest.builder().shippingAddressId(10L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(address));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"city\":\"Bengaluru\"}");
        when(inventoryService.reserveStock(100L, 2, null)).thenReturn("res-token-123");
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            o.setId(99L);
            return o;
        });

        OrderDetailDto res = orderService.createOrder(1L, req);

        assertNotNull(res);
        assertEquals(OrderStatus.PAYMENT_PENDING, res.getStatus());
        assertEquals(new BigDecimal("3000.00"), res.getSubtotal()); // 1500 * 2
        assertEquals(BigDecimal.ZERO, res.getShippingFee()); // Free over INR 999
        assertEquals(new BigDecimal("3000.00"), res.getTotalAmount());

        verify(inventoryService).reserveStock(100L, 2, null);
        verify(cartItemRepository).deleteByCartId(5L);
    }

    @Test
    @DisplayName("Create COD order sets status CONFIRMED and records CASH_ON_DELIVERY payment")
    void testCreateCodOrderSuccess() throws Exception {
        CreateOrderRequest req = CreateOrderRequest.builder()
                .shippingAddressId(10L)
                .paymentMethod("CASH_ON_DELIVERY")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(address));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"city\":\"Bengaluru\"}");
        when(inventoryService.reserveStock(100L, 2, null)).thenReturn("res-token-123");
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            o.setId(101L);
            return o;
        });

        OrderDetailDto res = orderService.createOrder(1L, req);

        assertNotNull(res);
        assertEquals(OrderStatus.CONFIRMED, res.getStatus());
        assertEquals("CASH_ON_DELIVERY", res.getPaymentMethod());
        assertEquals("PENDING", res.getPaymentStatus());
        verify(paymentRepository).save(any());
    }

    @Test
    @DisplayName("Cancelling order transitions status to CANCELLED")
    void testCancelOrderSuccess() {
        Order order = Order.builder()
                .id(99L)
                .orderNumber("ORD-20261031-123456")
                .user(user)
                .status(OrderStatus.CONFIRMED)
                .build();

        when(orderRepository.findByOrderNumberWithItems("ORD-20261031-123456")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderDetailDto res = orderService.cancelOrder(1L, "ORD-20261031-123456", "Customer changed mind", false);

        assertNotNull(res);
        assertEquals(OrderStatus.CANCELLED, res.getStatus());
        assertEquals("Customer changed mind", res.getCancellationReason());
    }

    @Test
    @DisplayName("Throws BadRequestException when cancelling already shipped or delivered order")
    void testCancelOrderDisallowedStatus() {
        Order order = Order.builder()
                .id(99L)
                .orderNumber("ORD-20261031-123456")
                .user(user)
                .status(OrderStatus.SHIPPED)
                .build();

        when(orderRepository.findByOrderNumberWithItems("ORD-20261031-123456")).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () -> orderService.cancelOrder(1L, "ORD-20261031-123456", "Too late", false));
    }
}

