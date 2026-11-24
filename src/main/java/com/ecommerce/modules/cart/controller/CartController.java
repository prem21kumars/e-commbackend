package com.ecommerce.modules.cart.controller;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.modules.cart.dto.AddCartItemRequest;
import com.ecommerce.modules.cart.dto.CartDto;
import com.ecommerce.modules.cart.dto.UpdateCartItemRequest;
import com.ecommerce.modules.cart.service.CartService;
import com.ecommerce.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Shopping Cart", description = "Endpoints for managing the customer persistent shopping cart")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "View Shopping Cart", description = "Retrieves cart with fresh server-calculated subtotals and real-time inventory validation")
    public ResponseEntity<ApiResponse<CartDto>> getCart(@AuthenticationPrincipal UserPrincipal principal) {
        CartDto cart = cartService.getCart(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add Item to Cart", description = "Adds a product item with quantity to cart, validating stock limits")
    public ResponseEntity<ApiResponse<CartDto>> addItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        CartDto updatedCart = cartService.addItem(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", updatedCart));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update Cart Item Quantity", description = "Updates quantity of a specific item in the cart")
    public ResponseEntity<ApiResponse<CartDto>> updateItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        CartDto updatedCart = cartService.updateItem(principal.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Cart item quantity updated", updatedCart));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove Item from Cart", description = "Deletes an item line from the cart")
    public ResponseEntity<ApiResponse<CartDto>> removeItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        CartDto updatedCart = cartService.removeItem(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", updatedCart));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Clear Shopping Cart", description = "Removes all items from the current customer's cart")
    public ResponseEntity<ApiResponse<Void>> clearCart(@AuthenticationPrincipal UserPrincipal principal) {
        cartService.clearCart(principal.getId());
        return ResponseEntity.ok(ApiResponse.messageOnly("Cart cleared successfully"));
    }
}

