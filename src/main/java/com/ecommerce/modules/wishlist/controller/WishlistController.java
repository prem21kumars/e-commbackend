package com.ecommerce.modules.wishlist.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.modules.wishlist.dto.WishlistDto;
import com.ecommerce.modules.wishlist.service.WishlistService;
import com.ecommerce.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
@Tag(name = "Wishlist", description = "Endpoints for managing saved items and customer favorites")
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "View Wishlist", description = "Retrieves all saved items in the customer's wishlist")
    public ResponseEntity<ApiResponse<WishlistDto>> getWishlist(@AuthenticationPrincipal UserPrincipal principal) {
        WishlistDto wishlist = wishlistService.getWishlist(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(wishlist));
    }

    @PostMapping("/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add Product to Wishlist", description = "Saves a product to the customer's wishlist")
    public ResponseEntity<ApiResponse<WishlistDto>> addToWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long productId
    ) {
        WishlistDto updated = wishlistService.addToWishlist(principal.getId(), productId);
        return ResponseEntity.ok(ApiResponse.success("Product saved to wishlist", updated));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove Product from Wishlist", description = "Removes a product from the customer's wishlist")
    public ResponseEntity<ApiResponse<WishlistDto>> removeFromWishlist(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long productId
    ) {
        WishlistDto updated = wishlistService.removeFromWishlist(principal.getId(), productId);
        return ResponseEntity.ok(ApiResponse.success("Product removed from wishlist", updated));
    }

    @GetMapping("/check/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check Product Saved Status", description = "Checks whether a specific product is already in customer's wishlist")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long productId
    ) {
        boolean saved = wishlistService.isInWishlist(principal.getId(), productId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("isSaved", saved)));
    }
}

