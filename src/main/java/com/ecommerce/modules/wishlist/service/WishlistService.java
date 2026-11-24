package com.ecommerce.modules.wishlist.service;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.product.entity.Product;
import com.ecommerce.modules.product.entity.ProductImage;
import com.ecommerce.modules.product.repository.ProductRepository;
import com.ecommerce.modules.wishlist.dto.WishlistDto;
import com.ecommerce.modules.wishlist.dto.WishlistItemDto;
import com.ecommerce.modules.wishlist.entity.Wishlist;
import com.ecommerce.modules.wishlist.entity.WishlistItem;
import com.ecommerce.modules.wishlist.repository.WishlistItemRepository;
import com.ecommerce.modules.wishlist.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public WishlistDto getWishlist(Long userId) {
        Wishlist wishlist = getOrCreateWishlist(userId);
        return mapToDto(wishlist);
    }

    @Transactional
    public WishlistDto addToWishlist(Long userId, Long productId) {
        Wishlist wishlist = getOrCreateWishlist(userId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        if (wishlistItemRepository.findByWishlistIdAndProductId(wishlist.getId(), productId).isPresent()) {
            throw new ConflictException("Product is already saved in your wishlist");
        }

        WishlistItem item = WishlistItem.builder()
                .wishlist(wishlist)
                .product(product)
                .build();
        wishlistItemRepository.save(item);
        wishlist.getItems().add(item);

        return mapToDto(wishlist);
    }

    @Transactional
    public WishlistDto removeFromWishlist(Long userId, Long productId) {
        Wishlist wishlist = getOrCreateWishlist(userId);
        wishlistItemRepository.deleteByWishlistIdAndProductId(wishlist.getId(), productId);
        wishlist.getItems().removeIf(item -> item.getProduct().getId().equals(productId));
        return mapToDto(wishlist);
    }

    @Transactional(readOnly = true)
    public boolean isInWishlist(Long userId, Long productId) {
        return wishlistItemRepository.existsByWishlistUserIdAndProductId(userId, productId);
    }

    private Wishlist getOrCreateWishlist(Long userId) {
        return wishlistRepository.findByUserIdWithItems(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                    Wishlist newWishlist = Wishlist.builder()
                            .user(user)
                            .items(new ArrayList<>())
                            .build();
                    return wishlistRepository.save(newWishlist);
                });
    }

    private WishlistDto mapToDto(Wishlist wishlist) {
        List<WishlistItemDto> itemDtos = wishlist.getItems().stream()
                .map(item -> {
                    Product p = item.getProduct();
                    int available = (p.getInventory() != null) ? p.getInventory().getAvailableToSell() : 0;
                    String primaryImg = p.getImages().stream()
                            .filter(ProductImage::isPrimary)
                            .map(ProductImage::getImageUrl)
                            .findFirst()
                            .orElse(p.getImages().isEmpty() ? null : p.getImages().getFirst().getImageUrl());

                    return WishlistItemDto.builder()
                            .id(item.getId())
                            .productId(p.getId())
                            .productName(p.getName())
                            .productSlug(p.getSlug())
                            .productSku(p.getSku())
                            .imageUrl(primaryImg)
                            .price(p.getEffectivePrice())
                            .inStock(available > 0)
                            .addedAt(item.getAddedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return WishlistDto.builder()
                .id(wishlist.getId())
                .userId(wishlist.getUser().getId())
                .items(itemDtos)
                .totalItems(itemDtos.size())
                .build();
    }
}

