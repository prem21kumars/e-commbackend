package com.ecommerce.modules.review.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.common.response.PagedResponse;
import com.ecommerce.modules.review.dto.CreateReviewRequest;
import com.ecommerce.modules.review.dto.ProductReviewDto;
import com.ecommerce.modules.review.service.ProductReviewService;
import com.ecommerce.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Product Reviews", description = "Endpoints for customer ratings, reviews, verified purchase checks, and moderation")
public class ProductReviewController {

    private final ProductReviewService reviewService;

    @PostMapping("/products/{id}/reviews")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit Product Review", description = "Submits a customer rating and review with automatic verified purchase detection")
    public ResponseEntity<ApiResponse<ProductReviewDto>> addReview(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        ProductReviewDto review = reviewService.addReview(principal.getId(), id, request);
        return new ResponseEntity<>(ApiResponse.success("Review submitted successfully", review), HttpStatus.CREATED);
    }

    @GetMapping("/products/{id}/reviews")
    @Operation(summary = "Get Product Reviews", description = "Paginated list of approved customer reviews for a product")
    public ResponseEntity<ApiResponse<PagedResponse<ProductReviewDto>>> getReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<ProductReviewDto> reviews = reviewService.getProductReviews(id, pageable);
        return ResponseEntity.ok(ApiResponse.success(reviews));
    }

    @PatchMapping("/admin/reviews/{id}/approval")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Moderate Review (Admin)", description = "Approves or suppresses a customer review")
    public ResponseEntity<ApiResponse<Void>> setApproval(
            @PathVariable Long id,
            @RequestParam boolean approved
    ) {
        reviewService.setApprovalStatus(id, approved);
        return ResponseEntity.ok(ApiResponse.messageOnly("Review moderation status updated"));
    }
}

