package com.ecommerce.modules.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewDto {
    private Long id;
    private Long productId;
    private Long userId;
    private String reviewerName;
    private int rating;
    private String title;
    private String comment;
    private boolean verifiedPurchase;
    private Instant createdAt;
}

