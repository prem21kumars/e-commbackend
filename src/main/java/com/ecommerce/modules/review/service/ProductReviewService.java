package com.ecommerce.modules.review.service;

import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.response.PagedResponse;
import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.order.entity.OrderStatus;
import com.ecommerce.modules.order.repository.OrderRepository;
import com.ecommerce.modules.product.entity.Product;
import com.ecommerce.modules.product.repository.ProductRepository;
import com.ecommerce.modules.review.dto.CreateReviewRequest;
import com.ecommerce.modules.review.dto.ProductReviewDto;
import com.ecommerce.modules.review.entity.ProductReview;
import com.ecommerce.modules.review.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public ProductReviewDto addReview(Long userId, Long productId, CreateReviewRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new ConflictException("You have already submitted a review for this product");
        }

        // Verified purchase check
        boolean verifiedPurchase = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
                .stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED || o.getStatus() == OrderStatus.CONFIRMED)
                .flatMap(o -> o.getItems().stream())
                .anyMatch(item -> item.getProduct().getId().equals(productId));

        ProductReview review = ProductReview.builder()
                .product(product)
                .user(user)
                .rating(request.getRating())
                .title(request.getTitle())
                .comment(request.getComment())
                .verifiedPurchase(verifiedPurchase)
                .approved(true) // Auto-approve or queue for moderation
                .build();
        ProductReview saved = reviewRepository.save(review);

        // Update product average rating & total reviews
        Double avgRating = reviewRepository.calculateAverageRating(productId);
        int totalReviews = reviewRepository.countApprovedReviewsByProductId(productId);

        product.setAverageRating(avgRating != null
                ? BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        product.setTotalReviews(totalReviews);
        productRepository.save(product);

        log.info("Review created for product {}: rating={}, verified={}", productId, request.getRating(), verifiedPurchase);
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductReviewDto> getProductReviews(Long productId, Pageable pageable) {
        Page<ProductReview> page = reviewRepository.findByProductIdAndApprovedTrueOrderByCreatedAtDesc(productId, pageable);
        return PagedResponse.from(page.map(this::mapToDto));
    }

    @Transactional
    public void setApprovalStatus(Long reviewId, boolean approved) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
        review.setApproved(approved);
        reviewRepository.save(review);

        // Recalculate
        Long productId = review.getProduct().getId();
        Double avgRating = reviewRepository.calculateAverageRating(productId);
        int totalReviews = reviewRepository.countApprovedReviewsByProductId(productId);
        Product product = review.getProduct();
        product.setAverageRating(avgRating != null
                ? BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        product.setTotalReviews(totalReviews);
        productRepository.save(product);
    }

    private ProductReviewDto mapToDto(ProductReview r) {
        String reviewer = r.getUser().getFirstName() + " " + r.getUser().getLastName().charAt(0) + ".";
        return ProductReviewDto.builder()
                .id(r.getId())
                .productId(r.getProduct().getId())
                .userId(r.getUser().getId())
                .reviewerName(reviewer)
                .rating(r.getRating())
                .title(r.getTitle())
                .comment(r.getComment())
                .verifiedPurchase(r.isVerifiedPurchase())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

