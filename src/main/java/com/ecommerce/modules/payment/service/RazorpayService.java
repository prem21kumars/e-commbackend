package com.ecommerce.modules.payment.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.PaymentException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.modules.inventory.service.InventoryService;
import com.ecommerce.modules.order.entity.Order;
import com.ecommerce.modules.order.entity.OrderStatus;
import com.ecommerce.modules.order.repository.OrderRepository;
import com.ecommerce.modules.payment.dto.*;
import com.ecommerce.modules.payment.entity.*;
import com.ecommerce.modules.payment.repository.PaymentRepository;
import com.ecommerce.modules.payment.repository.PaymentTransactionRepository;
import com.ecommerce.modules.payment.repository.PaymentWebhookEventRepository;
import com.ecommerce.modules.payment.repository.RefundRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final RefundRepository refundRepository;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @Value("${app.razorpay.key-id:rzp_test_placeholder}")
    private String keyId;

    @Value("${app.razorpay.key-secret:placeholder_secret}")
    private String keySecret;

    @Value("${app.razorpay.webhook-secret:placeholder_webhook_secret}")
    private String webhookSecret;

    @Value("${app.razorpay.currency:INR}")
    private String currency;

    @Transactional
    public RazorpayOrderResponseDto createRazorpayOrder(String orderNumber, Long userId) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Order does not belong to the authenticated customer");
        }

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BadRequestException("Order is not awaiting payment (current status: " + order.getStatus() + ")");
        }

        // Check if an active pending payment record already exists for this order
        Optional<Payment> existingPayment = paymentRepository.findByOrderId(order.getId());
        if (existingPayment.isPresent() && existingPayment.get().getStatus() == PaymentStatus.COMPLETED) {
            throw new ConflictException("Order has already been paid for");
        }

        long amountInPaise = order.getTotalAmount().multiply(new BigDecimal("100")).longValue();

        String razorpayOrderId;
        try {
            if ("rzp_test_placeholder".equals(keyId) || keySecret.contains("placeholder")) {
                // Mock test mode if production gateway keys are not injected
                razorpayOrderId = "order_mock_" + UUID.randomUUID().toString().substring(0, 14);
            } else {
                RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);
                JSONObject orderRequest = new JSONObject();
                orderRequest.put("amount", amountInPaise);
                orderRequest.put("currency", currency);
                orderRequest.put("receipt", orderNumber);
                orderRequest.put("notes", new JSONObject().put("orderNumber", orderNumber));

                com.razorpay.Order rzpOrder = razorpay.orders.create(orderRequest);
                razorpayOrderId = rzpOrder.get("id");
            }
        } catch (Exception e) {
            log.error("Failed to create Razorpay order for orderNumber={}", orderNumber, e);
            throw new PaymentException("Failed to initiate Razorpay checkout: " + e.getMessage());
        }

        Payment payment = existingPayment.orElseGet(() -> Payment.builder()
                .order(order)
                .paymentMethod(PaymentMethod.RAZORPAY)
                .amount(order.getTotalAmount())
                .currency(currency)
                .build());

        payment.setGatewayOrderId(razorpayOrderId);
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        return RazorpayOrderResponseDto.builder()
                .razorpayOrderId(razorpayOrderId)
                .orderNumber(orderNumber)
                .amount(order.getTotalAmount())
                .amountInPaise(amountInPaise)
                .currency(currency)
                .razorpayKeyId(keyId)
                .customerName(order.getUser().getFirstName() + " " + order.getUser().getLastName())
                .customerEmail(order.getUser().getEmail())
                .customerPhone(order.getUser().getPhoneNumber())
                .build();
    }

    @Transactional
    public PaymentStatusDto verifyPayment(VerifyPaymentRequest request, Long userId) {
        Payment payment = paymentRepository.findByGatewayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "gatewayOrderId", request.getRazorpayOrderId()));

        Order order = payment.getOrder();
        if (userId != null && !order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to payment verification");
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return buildStatusDto(payment, "Payment has already been confirmed successfully");
        }

        // Verify cryptographic HMAC-SHA256 signature
        boolean signatureValid = verifySignature(
                request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId(),
                request.getRazorpaySignature(),
                keySecret
        );

        if (!signatureValid && !keySecret.contains("placeholder")) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorCode("SIGNATURE_VERIFICATION_FAILED");
            payment.setErrorDescription("Razorpay HMAC signature mismatch");
            paymentRepository.save(payment);
            throw new PaymentException("Payment signature verification failed. Potential tampering detected.");
        }

        // Successful Payment Confirmation
        payment.setGatewayPaymentId(request.getRazorpayPaymentId());
        payment.setGatewaySignature(request.getRazorpaySignature());
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(Instant.now());
        Payment savedPayment = paymentRepository.save(payment);

        // Transition Order to CONFIRMED
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        // Record transaction audit
        PaymentTransaction transaction = PaymentTransaction.builder()
                .payment(savedPayment)
                .transactionType("CHARGE")
                .amount(payment.getAmount())
                .status("SUCCESS")
                .gatewayTransactionId(request.getRazorpayPaymentId())
                .build();
        transactionRepository.save(transaction);

        log.info("Payment confirmed successfully: order={}, razorpayPaymentId={}", order.getOrderNumber(), request.getRazorpayPaymentId());
        return buildStatusDto(savedPayment, "Payment successfully verified and order confirmed");
    }

    @Transactional
    public void processWebhook(String rawPayload, String signature) {
        // 1. Verify webhook HMAC signature
        if (!keySecret.contains("placeholder")) {
            boolean validSignature = verifySignature(rawPayload, signature, webhookSecret);
            if (!validSignature) {
                log.warn("Invalid Razorpay webhook signature detected!");
                throw new BadRequestException("Invalid webhook signature");
            }
        }

        // 2. Parse payload
        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            throw new BadRequestException("Malformed webhook JSON payload: " + e.getMessage());
        }

        String event = root.path("event").asText("");
        String eventId = root.path("id").asText(UUID.randomUUID().toString());

        // 3. Idempotency guard: prevent duplicate replay processing
        if (webhookEventRepository.existsByEventId(eventId)) {
            log.info("Webhook event {} already processed (idempotent ignore)", eventId);
            return;
        }

        PaymentWebhookEvent webhookEvent = PaymentWebhookEvent.builder()
                .eventId(eventId)
                .eventType(event)
                .payload(rawPayload)
                .processedStatus("PENDING")
                .build();
        webhookEventRepository.save(webhookEvent);

        try {
            if ("order.paid".equals(event) || "payment.captured".equals(event)) {
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                String gatewayOrderId = paymentEntity.path("order_id").asText();
                String gatewayPaymentId = paymentEntity.path("id").asText();

                if (!gatewayOrderId.isBlank()) {
                    paymentRepository.findByGatewayOrderId(gatewayOrderId).ifPresent(payment -> {
                        if (payment.getStatus() != PaymentStatus.COMPLETED) {
                            payment.setGatewayPaymentId(gatewayPaymentId);
                            payment.setStatus(PaymentStatus.COMPLETED);
                            payment.setPaidAt(Instant.now());
                            paymentRepository.save(payment);

                            Order order = payment.getOrder();
                            order.setStatus(OrderStatus.CONFIRMED);
                            orderRepository.save(order);
                            log.info("Webhook confirmed payment for order {}", order.getOrderNumber());
                        }
                    });
                }
            } else if ("payment.failed".equals(event)) {
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                String gatewayOrderId = paymentEntity.path("order_id").asText();
                String errorCode = paymentEntity.path("error_code").asText();
                String errorDesc = paymentEntity.path("error_description").asText();

                paymentRepository.findByGatewayOrderId(gatewayOrderId).ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setErrorCode(errorCode);
                    payment.setErrorDescription(errorDesc);
                    paymentRepository.save(payment);
                });
            }

            webhookEvent.setProcessedStatus("PROCESSED");
            webhookEvent.setProcessedAt(Instant.now());
            webhookEventRepository.save(webhookEvent);
        } catch (Exception e) {
            log.error("Failed to process webhook event {}", eventId, e);
            webhookEvent.setProcessedStatus("FAILED");
            webhookEvent.setErrorMessage(e.getMessage());
            webhookEventRepository.save(webhookEvent);
            throw new PaymentException("Webhook processing error: " + e.getMessage());
        }
    }

    @Transactional
    public RefundDto processRefund(String orderNumber, ProcessRefundRequest request, String performedBy) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BadRequestException("No payment record found for order " + orderNumber));

        if (payment.getStatus() != PaymentStatus.COMPLETED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BadRequestException("Only completed payments can be refunded (current: " + payment.getStatus() + ")");
        }

        if (request.getAmount().compareTo(payment.getAmount()) > 0) {
            throw new BadRequestException("Refund amount exceeds original payment amount");
        }

        String gatewayRefundId = "rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        Refund refund = Refund.builder()
                .order(order)
                .payment(payment)
                .refundAmount(request.getAmount())
                .status("PROCESSED")
                .gatewayRefundId(gatewayRefundId)
                .reason(request.getReason())
                .processedAt(Instant.now())
                .createdBy(performedBy != null ? performedBy : "ADMIN")
                .build();
        Refund savedRefund = refundRepository.save(refund);

        payment.setStatus(request.getAmount().compareTo(payment.getAmount()) == 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED);
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .payment(payment)
                .transactionType("REFUND")
                .amount(request.getAmount())
                .status("SUCCESS")
                .gatewayTransactionId(gatewayRefundId)
                .build();
        transactionRepository.save(transaction);

        return RefundDto.builder()
                .id(savedRefund.getId())
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .refundAmount(request.getAmount())
                .status("PROCESSED")
                .gatewayRefundId(gatewayRefundId)
                .reason(request.getReason())
                .processedAt(savedRefund.getProcessedAt())
                .createdBy(savedRefund.getCreatedBy())
                .build();
    }

    private boolean verifySignature(String payload, String expectedSignature, String secret) {
        if (expectedSignature == null || secret == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String generated = HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(generated.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("HMAC verification failed", e);
            return false;
        }
    }

    private PaymentStatusDto buildStatusDto(Payment p, String message) {
        return PaymentStatusDto.builder()
                .orderNumber(p.getOrder().getOrderNumber())
                .gatewayOrderId(p.getGatewayOrderId())
                .gatewayPaymentId(p.getGatewayPaymentId())
                .status(p.getStatus())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .paidAt(p.getPaidAt())
                .message(message)
                .build();
    }
}

