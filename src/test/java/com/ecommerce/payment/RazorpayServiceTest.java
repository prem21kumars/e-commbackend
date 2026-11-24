package com.ecommerce.payment;

import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.inventory.service.InventoryService;
import com.ecommerce.modules.order.entity.Order;
import com.ecommerce.modules.order.entity.OrderStatus;
import com.ecommerce.modules.order.repository.OrderRepository;
import com.ecommerce.modules.payment.dto.CreateRazorpayOrderRequest;
import com.ecommerce.modules.payment.dto.PaymentStatusDto;
import com.ecommerce.modules.payment.dto.RazorpayOrderResponseDto;
import com.ecommerce.modules.payment.dto.VerifyPaymentRequest;
import com.ecommerce.modules.payment.entity.Payment;
import com.ecommerce.modules.payment.entity.PaymentStatus;
import com.ecommerce.modules.payment.repository.PaymentRepository;
import com.ecommerce.modules.payment.repository.PaymentTransactionRepository;
import com.ecommerce.modules.payment.repository.PaymentWebhookEventRepository;
import com.ecommerce.modules.payment.repository.RefundRepository;
import com.ecommerce.modules.payment.service.RazorpayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RazorpayService Unit Tests")
class RazorpayServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentTransactionRepository transactionRepository;
    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RazorpayService razorpayService;

    private User user;
    private Order order;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(razorpayService, "keyId", "rzp_test_placeholder");
        ReflectionTestUtils.setField(razorpayService, "keySecret", "placeholder_secret");
        ReflectionTestUtils.setField(razorpayService, "webhookSecret", "placeholder_webhook_secret");
        ReflectionTestUtils.setField(razorpayService, "currency", "INR");

        user = User.builder().id(1L).email("buyer@example.com").firstName("Buyer").lastName("One").build();
        order = Order.builder()
                .id(10L)
                .orderNumber("ORD-20261031-999999")
                .user(user)
                .status(OrderStatus.PAYMENT_PENDING)
                .totalAmount(new BigDecimal("1250.00"))
                .currency("INR")
                .build();
    }

    @Test
    @DisplayName("Create Razorpay order calculates exact paise amount (INR 1250.00 = 125000 paise)")
    void testCreateRazorpayOrderCalculatesPaise() {
        when(orderRepository.findByOrderNumber("ORD-20261031-999999")).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        RazorpayOrderResponseDto response = razorpayService.createRazorpayOrder("ORD-20261031-999999", 1L);

        assertNotNull(response);
        assertEquals(125000L, response.getAmountInPaise());
        assertEquals(new BigDecimal("1250.00"), response.getAmount());
        assertEquals("ORD-20261031-999999", response.getOrderNumber());
        assertNotNull(response.getRazorpayOrderId());

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Verify payment updates Payment to COMPLETED and Order to CONFIRMED")
    void testVerifyPaymentConfirmsOrder() {
        Payment payment = Payment.builder()
                .id(50L)
                .order(order)
                .gatewayOrderId("order_xyz123")
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("1250.00"))
                .currency("INR")
                .build();

        when(paymentRepository.findByGatewayOrderId("order_xyz123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        VerifyPaymentRequest req = VerifyPaymentRequest.builder()
                .razorpayOrderId("order_xyz123")
                .razorpayPaymentId("pay_abc789")
                .razorpaySignature("sig_dummy")
                .build();

        PaymentStatusDto status = razorpayService.verifyPayment(req, 1L);

        assertNotNull(status);
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals("pay_abc789", payment.getGatewayPaymentId());
        assertNotNull(payment.getPaidAt());

        verify(orderRepository).save(order);
        verify(paymentRepository).save(payment);
        verify(transactionRepository).save(any());
    }

    @Test
    @DisplayName("Webhook processor ignores duplicate event id (idempotency guard)")
    void testWebhookIdempotentDeduplication() throws Exception {
        String rawJson = "{\"id\":\"evt_duplicate_123\",\"event\":\"payment.captured\"}";

        when(webhookEventRepository.existsByEventId("evt_duplicate_123")).thenReturn(true);
        when(objectMapper.readTree(rawJson)).thenReturn(new ObjectMapper().readTree(rawJson));

        razorpayService.processWebhook(rawJson, "sig_dummy");

        // Verify no payment updates occur for duplicate event!
        verify(paymentRepository, never()).findByGatewayOrderId(anyString());
    }
}

