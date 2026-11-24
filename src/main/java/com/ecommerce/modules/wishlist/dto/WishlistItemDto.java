package com.ecommerce.modules.wishlist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemDto {
    private Long id;
    private Long productId;
    private String productName;
    private String productSlug;
    private String productSku;
    private String imageUrl;
    private BigDecimal price;
    private boolean inStock;
    private Instant addedAt;
}

