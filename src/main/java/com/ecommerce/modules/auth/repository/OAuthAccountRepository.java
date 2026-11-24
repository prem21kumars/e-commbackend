package com.ecommerce.modules.auth.repository;

import com.ecommerce.modules.auth.entity.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {
    Optional<OAuthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
    Optional<OAuthAccount> findByUserIdAndProvider(Long userId, String provider);
}

