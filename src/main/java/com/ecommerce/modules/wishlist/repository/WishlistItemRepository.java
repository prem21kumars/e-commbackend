package com.ecommerce.modules.wishlist.repository;

import com.ecommerce.modules.wishlist.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    Optional<WishlistItem> findByWishlistIdAndProductId(Long wishlistId, Long productId);
    boolean existsByWishlistUserIdAndProductId(Long userId, Long productId);
    void deleteByWishlistIdAndProductId(Long wishlistId, Long productId);
}

