package com.ecommerce.modules.customer.repository;

import com.ecommerce.modules.customer.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<CustomerAddress> findByIdAndUserId(Long id, Long userId);

    Optional<CustomerAddress> findByUserIdAndDefaultShippingTrue(Long userId);

    @Modifying
    @Query("UPDATE CustomerAddress a SET a.defaultShipping = false WHERE a.user.id = :userId")
    void resetDefaultShipping(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE CustomerAddress a SET a.defaultBilling = false WHERE a.user.id = :userId")
    void resetDefaultBilling(@Param("userId") Long userId);
}

