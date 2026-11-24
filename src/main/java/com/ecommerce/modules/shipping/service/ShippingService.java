package com.ecommerce.modules.shipping.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.modules.order.entity.Order;
import com.ecommerce.modules.order.entity.OrderStatus;
import com.ecommerce.modules.order.repository.OrderRepository;
import com.ecommerce.modules.shipping.dto.*;
import com.ecommerce.modules.shipping.entity.Shipment;
import com.ecommerce.modules.shipping.entity.ShipmentStatus;
import com.ecommerce.modules.shipping.entity.ShipmentTracking;
import com.ecommerce.modules.shipping.repository.ShipmentRepository;
import com.ecommerce.modules.shipping.repository.ShipmentTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingRepository trackingRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public ShipmentDto createShipment(Long orderId, CreateShipmentRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PROCESSING) {
            throw new BadRequestException("Shipment can only be created for confirmed or processing orders");
        }

        Shipment shipment = Shipment.builder()
                .order(order)
                .carrier(request.getCarrier().trim())
                .trackingNumber(request.getTrackingNumber().trim())
                .status(ShipmentStatus.CREATED)
                .shippingMethod(request.getShippingMethod())
                .estimatedDelivery(request.getEstimatedDelivery())
                .build();
        Shipment savedShipment = shipmentRepository.save(shipment);

        order.setStatus(OrderStatus.PROCESSING);
        orderRepository.save(order);

        ShipmentTracking event = ShipmentTracking.builder()
                .shipment(savedShipment)
                .status("CREATED")
                .statusDescription("Shipment created with carrier " + request.getCarrier())
                .build();
        trackingRepository.save(event);

        return mapToDto(savedShipment);
    }

    @Transactional
    public ShipmentDto updateShipmentStatus(Long shipmentId, UpdateShipmentStatusRequest request) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", shipmentId));

        shipment.setStatus(request.getStatus());

        if (request.getStatus() == ShipmentStatus.IN_TRANSIT && shipment.getShippedAt() == null) {
            shipment.setShippedAt(Instant.now());
            shipment.getOrder().setStatus(OrderStatus.SHIPPED);
        } else if (request.getStatus() == ShipmentStatus.OUT_FOR_DELIVERY) {
            shipment.getOrder().setStatus(OrderStatus.OUT_FOR_DELIVERY);
        } else if (request.getStatus() == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(Instant.now());
            shipment.getOrder().setStatus(OrderStatus.DELIVERED);
        }
        orderRepository.save(shipment.getOrder());

        Shipment savedShipment = shipmentRepository.save(shipment);

        ShipmentTracking event = ShipmentTracking.builder()
                .shipment(savedShipment)
                .status(request.getStatus().name())
                .location(request.getLocation())
                .statusDescription(request.getStatusDescription())
                .build();
        trackingRepository.save(event);

        return mapToDto(savedShipment);
    }

    @Transactional(readOnly = true)
    public ShipmentDto getShipmentByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        Shipment shipment = shipmentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipment for order", "orderNumber", orderNumber));

        return mapToDto(shipment);
    }

    @Transactional(readOnly = true)
    public ShipmentDto getShipmentByTrackingNumber(String trackingNumber) {
        Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "trackingNumber", trackingNumber));

        return mapToDto(shipment);
    }

    private ShipmentDto mapToDto(Shipment s) {
        List<ShipmentTrackingDto> history = trackingRepository.findByShipmentIdOrderByRecordedAtDesc(s.getId())
                .stream()
                .map(t -> ShipmentTrackingDto.builder()
                        .id(t.getId())
                        .status(t.getStatus())
                        .location(t.getLocation())
                        .statusDescription(t.getStatusDescription())
                        .recordedAt(t.getRecordedAt())
                        .build())
                .collect(Collectors.toList());

        return ShipmentDto.builder()
                .id(s.getId())
                .orderId(s.getOrder().getId())
                .orderNumber(s.getOrder().getOrderNumber())
                .carrier(s.getCarrier())
                .trackingNumber(s.getTrackingNumber())
                .status(s.getStatus())
                .shippingMethod(s.getShippingMethod())
                .shippedAt(s.getShippedAt())
                .estimatedDelivery(s.getEstimatedDelivery())
                .deliveredAt(s.getDeliveredAt())
                .trackingHistory(history)
                .build();
    }
}

