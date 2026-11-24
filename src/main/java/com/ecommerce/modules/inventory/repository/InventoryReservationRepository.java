package com.ecommerce.modules.inventory.repository;

import com.ecommerce.modules.inventory.entity.InventoryReservation;
import com.ecommerce.modules.inventory.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByReservationToken(String reservationToken);
    List<InventoryReservation> findByOrderId(Long orderId);
    List<InventoryReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant now);
}

