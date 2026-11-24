package com.ecommerce.modules.payment.repository;

import com.ecommerce.modules.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByOrderId(Long orderId);
    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);
}

