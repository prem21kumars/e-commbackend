package com.ecommerce.modules.shipping.repository;

import com.ecommerce.modules.shipping.entity.ShipmentTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipmentTrackingRepository extends JpaRepository<ShipmentTracking, Long> {
    List<ShipmentTracking> findByShipmentIdOrderByRecordedAtDesc(Long shipmentId);
}

