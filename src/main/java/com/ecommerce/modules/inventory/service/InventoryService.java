package com.ecommerce.modules.inventory.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.InventoryException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.modules.inventory.dto.*;
import com.ecommerce.modules.inventory.entity.*;
import com.ecommerce.modules.inventory.repository.InventoryMovementRepository;
import com.ecommerce.modules.inventory.repository.InventoryRepository;
import com.ecommerce.modules.inventory.repository.InventoryReservationRepository;
import com.ecommerce.modules.product.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public InventoryDto getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", "productId", productId));
        return mapToDto(inventory);
    }

    @Transactional(readOnly = true)
    public StockAvailabilityDto checkAvailability(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", "productId", productId));
        int available = inventory.getAvailableToSell();
        return StockAvailabilityDto.builder()
                .productId(productId)
                .sku(inventory.getProduct().getSku())
                .availableToSell(available)
                .inStock(available > 0)
                .build();
    }

    @Transactional
    public InventoryDto adjustStock(Long productId, AdjustStockRequest request, String performedBy) {
        // Pessimistic lock for atomic stock modification
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", "productId", productId));

        int newQuantity = inventory.getQuantityAvailable() + request.getQuantityChange();
        if (newQuantity < 0) {
            throw new BadRequestException("Adjustment would cause negative inventory balance (current: "
                    + inventory.getQuantityAvailable() + ", adjustment: " + request.getQuantityChange() + ")");
        }

        inventory.setQuantityAvailable(newQuantity);
        Inventory saved = inventoryRepository.save(inventory);

        InventoryMovement movement = InventoryMovement.builder()
                .product(inventory.getProduct())
                .quantityChange(request.getQuantityChange())
                .resultingQuantity(newQuantity)
                .movementType(request.getMovementType())
                .referenceId(request.getReferenceId())
                .notes(request.getNotes())
                .createdBy(performedBy != null ? performedBy : "ADMIN")
                .build();
        movementRepository.save(movement);

        log.info("Stock adjusted for product {}: change={}, newAvailable={}",
                productId, request.getQuantityChange(), newQuantity);

        return mapToDto(saved);
    }

    /**
     * Atomically reserves inventory during checkout to protect against concurrent overselling.
     */
    @Transactional
    public String reserveStock(Long productId, int quantity, Long orderId) {
        if (quantity <= 0) {
            throw new BadRequestException("Reservation quantity must be positive");
        }

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", "productId", productId));

        if (inventory.getAvailableToSell() < quantity) {
            throw new InventoryException("Insufficient stock for product '" + inventory.getProduct().getName()
                    + "'. Requested: " + quantity + ", Available: " + inventory.getAvailableToSell());
        }

        // Hold reservation
        inventory.setQuantityReserved(inventory.getQuantityReserved() + quantity);
        inventoryRepository.save(inventory);

        String reservationToken = UUID.randomUUID().toString();
        InventoryReservation reservation = InventoryReservation.builder()
                .product(inventory.getProduct())
                .orderId(orderId)
                .reservationToken(reservationToken)
                .quantity(quantity)
                .status(ReservationStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(900)) // 15-minute hold
                .build();
        reservationRepository.save(reservation);

        log.info("Reserved {} units of product {} under token {}", quantity, productId, reservationToken);
        return reservationToken;
    }

    /**
     * Releases a pending stock reservation (e.g., checkout abandoned or payment failed).
     */
    @Transactional
    public void releaseReservation(String reservationToken) {
        InventoryReservation reservation = reservationRepository.findByReservationToken(reservationToken)
                .orElse(null);

        if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING) {
            return;
        }

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(reservation.getProduct().getId())
                .orElse(null);

        if (inventory != null) {
            int newReserved = Math.max(0, inventory.getQuantityReserved() - reservation.getQuantity());
            inventory.setQuantityReserved(newReserved);
            inventoryRepository.save(inventory);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        log.info("Released reservation {}", reservationToken);
    }

    /**
     * Commits a reservation upon confirmed payment: deducts stock from available and clears reserved.
     */
    @Transactional
    public void commitReservation(String reservationToken) {
        InventoryReservation reservation = reservationRepository.findByReservationToken(reservationToken)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "token", reservationToken));

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BadRequestException("Reservation " + reservationToken + " is not pending commit");
        }

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(reservation.getProduct().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", reservation.getProduct().getId()));

        int qty = reservation.getQuantity();
        inventory.setQuantityAvailable(inventory.getQuantityAvailable() - qty);
        inventory.setQuantityReserved(Math.max(0, inventory.getQuantityReserved() - qty));
        inventoryRepository.save(inventory);

        reservation.setStatus(ReservationStatus.COMMITTED);
        reservationRepository.save(reservation);

        InventoryMovement movement = InventoryMovement.builder()
                .product(inventory.getProduct())
                .quantityChange(-qty)
                .resultingQuantity(inventory.getQuantityAvailable())
                .movementType(MovementType.ORDER_DEDUCTION)
                .referenceId(reservationToken)
                .notes("Order completed and confirmed")
                .createdBy("PAYMENT_SERVICE")
                .build();
        movementRepository.save(movement);

        log.info("Committed reservation {} for product {}: deducted {}", reservationToken, inventory.getProduct().getId(), qty);
    }

    @Transactional(readOnly = true)
    public Page<InventoryMovementDto> getMovements(Long productId, Pageable pageable) {
        return movementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(this::mapMovementToDto);
    }

    @Transactional(readOnly = true)
    public Page<InventoryDto> getLowStockAlerts(Pageable pageable) {
        return inventoryRepository.findLowStockInventories(pageable)
                .map(this::mapToDto);
    }

    private InventoryDto mapToDto(Inventory inv) {
        Product p = inv.getProduct();
        return InventoryDto.builder()
                .id(inv.getId())
                .productId(p.getId())
                .productName(p.getName())
                .productSku(p.getSku())
                .quantityAvailable(inv.getQuantityAvailable())
                .quantityReserved(inv.getQuantityReserved())
                .availableToSell(inv.getAvailableToSell())
                .lowStockThreshold(inv.getLowStockThreshold())
                .lowStock(inv.isLowStock())
                .build();
    }

    private InventoryMovementDto mapMovementToDto(InventoryMovement m) {
        return InventoryMovementDto.builder()
                .id(m.getId())
                .productId(m.getProduct().getId())
                .quantityChange(m.getQuantityChange())
                .resultingQuantity(m.getResultingQuantity())
                .movementType(m.getMovementType())
                .referenceId(m.getReferenceId())
                .notes(m.getNotes())
                .createdBy(m.getCreatedBy())
                .createdAt(m.getCreatedAt())
                .build();
    }
}

