package com.ecommerce.modules.auth.repository;

import com.ecommerce.modules.auth.entity.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {
    List<MfaRecoveryCode> findByUserIdAndIsUsedFalse(Long userId);
    void deleteByUserId(Long userId);
}

