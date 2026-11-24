package com.ecommerce.inventory;

import com.ecommerce.common.exception.InventoryException;
import com.ecommerce.modules.inventory.entity.Inventory;
import com.ecommerce.modules.inventory.entity.InventoryReservation;
import com.ecommerce.modules.inventory.entity.ReservationStatus;
import com.ecommerce.modules.inventory.repository.InventoryMovementRepository;
import com.ecommerce.modules.inventory.repository.InventoryRepository;
import com.ecommerce.modules.inventory.repository.InventoryReservationRepository;
import com.ecommerce.modules.inventory.service.InventoryService;
import com.ecommerce.modules.product.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Unit Tests")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryMovementRepository movementRepository;
    @Mock
    private InventoryReservationRepository reservationRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(10L).name("Teddy Bear").sku("TEDDY-01").build();
        inventory = Inventory.builder()
                .id(1L)
                .product(product)
                .quantityAvailable(20)
                .quantityReserved(5)
                .lowStockThreshold(5)
                .build();
    }

    @Test
    @DisplayName("Stock reservation succeeds when quantity is available")
    void testReserveStockSuccess() {
        when(inventoryRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(i -> i.getArgument(0));

        // Available to sell is 20 - 5 = 15. Reserve 10.
        String token = inventoryService.reserveStock(10L, 10, 500L);

        assertNotNull(token);
        assertEquals(15, inventory.getQuantityReserved());
        verify(inventoryRepository).save(inventory);
        verify(reservationRepository).save(any(InventoryReservation.class));
    }

    @Test
    @DisplayName("Stock reservation throws InventoryException when requested exceeds available")
    void testReserveStockInsufficient() {
        when(inventoryRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(inventory));

        // Available to sell is 15. Request 16.
        assertThrows(InventoryException.class, () -> inventoryService.reserveStock(10L, 16, 500L));

        assertEquals(5, inventory.getQuantityReserved()); // Unchanged
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Committing reservation deducts available and clears reserved stock")
    void testCommitReservation() {
        InventoryReservation res = InventoryReservation.builder()
                .reservationToken("token-abc")
                .product(product)
                .quantity(5)
                .status(ReservationStatus.PENDING)
                .build();

        when(reservationRepository.findByReservationToken("token-abc")).thenReturn(Optional.of(res));
        when(inventoryRepository.findByProductIdForUpdate(10L)).thenReturn(Optional.of(inventory));

        inventoryService.commitReservation("token-abc");

        assertEquals(15, inventory.getQuantityAvailable()); // 20 - 5
        assertEquals(0, inventory.getQuantityReserved());   // 5 - 5
        assertEquals(ReservationStatus.COMMITTED, res.getStatus());

        verify(inventoryRepository).save(inventory);
        verify(movementRepository).save(any());
    }
}

