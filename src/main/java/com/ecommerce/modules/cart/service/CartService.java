package com.ecommerce.modules.cart.service;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.cart.dto.AddCartItemRequest;
import com.ecommerce.modules.cart.dto.CartDto;
import com.ecommerce.modules.cart.dto.CartItemDto;
import com.ecommerce.modules.cart.dto.UpdateCartItemRequest;
import com.ecommerce.modules.cart.entity.Cart;
import com.ecommerce.modules.cart.entity.CartItem;
import com.ecommerce.modules.cart.repository.CartItemRepository;
import com.ecommerce.modules.cart.repository.CartRepository;
import com.ecommerce.modules.product.entity.Product;
import com.ecommerce.modules.product.entity.ProductImage;
import com.ecommerce.modules.product.entity.ProductStatus;
import com.ecommerce.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public CartDto getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return mapToCartDto(cart);
    }

    @Transactional
    public CartDto addItem(Long userId, AddCartItemRequest request) {
        Cart cart = getOrCreateCart(userId);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        if (product.getStatus() != ProductStatus.PUBLISHED) {
            throw new BadRequestException("Product is currently unavailable for purchase");
        }

        int available = (product.getInventory() != null) ? product.getInventory().getAvailableToSell() : 0;
        if (available < request.getQuantity()) {
            throw new BadRequestException("Requested quantity (" + request.getQuantity() +
                    ") exceeds available stock (" + available + ")");
        }

        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            int newQty = item.getQuantity() + request.getQuantity();
            if (available < newQty) {
                throw new BadRequestException("Combined quantity (" + newQty + ") exceeds available stock (" + available + ")");
            }
            item.setQuantity(newQty);
            // Re-sync with current authoritative effective price
            item.setUnitPrice(product.getEffectivePrice());
            cartItemRepository.save(item);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .unitPrice(product.getEffectivePrice())
                    .build();
            cartItemRepository.save(newItem);
            cart.getItems().add(newItem);
        }

        return mapToCartDto(cart);
    }

    @Transactional
    public CartDto updateItem(Long userId, Long cartItemId, UpdateCartItemRequest request) {
        CartItem item = cartItemRepository.findByIdAndCartUserId(cartItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", "id", cartItemId));

        Product product = item.getProduct();
        int available = (product.getInventory() != null) ? product.getInventory().getAvailableToSell() : 0;

        if (available < request.getQuantity()) {
            throw new BadRequestException("Requested quantity (" + request.getQuantity() +
                    ") exceeds available stock (" + available + ")");
        }

        item.setQuantity(request.getQuantity());
        item.setUnitPrice(product.getEffectivePrice()); // Re-validate authoritative price
        cartItemRepository.save(item);

        return mapToCartDto(item.getCart());
    }

    @Transactional
    public CartDto removeItem(Long userId, Long cartItemId) {
        CartItem item = cartItemRepository.findByIdAndCartUserId(cartItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", "id", cartItemId));

        Cart cart = item.getCart();
        cart.getItems().remove(item);
        cartItemRepository.delete(item);

        return mapToCartDto(cart);
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cartItemRepository.deleteByCartId(cart.getId());
        cart.getItems().clear();
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserIdWithItems(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                    Cart newCart = Cart.builder()
                            .user(user)
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    private CartDto mapToCartDto(Cart cart) {
        List<CartItemDto> itemDtos = cart.getItems().stream()
                .map(item -> {
                    Product p = item.getProduct();
                    int available = (p.getInventory() != null) ? p.getInventory().getAvailableToSell() : 0;
                    String primaryImg = p.getImages().stream()
                            .filter(ProductImage::isPrimary)
                            .map(ProductImage::getImageUrl)
                            .findFirst()
                            .orElse(p.getImages().isEmpty() ? null : p.getImages().getFirst().getImageUrl());

                    BigDecimal currentUnitPrice = p.getEffectivePrice(); // Fresh authoritative price
                    BigDecimal totalPrice = currentUnitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

                    return CartItemDto.builder()
                            .id(item.getId())
                            .productId(p.getId())
                            .productName(p.getName())
                            .productSlug(p.getSlug())
                            .productSku(p.getSku())
                            .imageUrl(primaryImg)
                            .quantity(item.getQuantity())
                            .unitPrice(currentUnitPrice)
                            .totalPrice(totalPrice)
                            .availableStock(available)
                            .inStock(available >= item.getQuantity())
                            .build();
                })
                .collect(Collectors.toList());

        BigDecimal subtotal = itemDtos.stream()
                .map(CartItemDto::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalQty = itemDtos.stream()
                .mapToInt(CartItemDto::getQuantity)
                .sum();

        // Standard shipping rule: Free shipping on orders over INR 999, else INR 99
        BigDecimal shippingFee = (subtotal.compareTo(new BigDecimal("999.00")) >= 0 || subtotal.compareTo(BigDecimal.ZERO) == 0)
                ? BigDecimal.ZERO
                : new BigDecimal("99.00");

        BigDecimal total = subtotal.add(shippingFee);
        boolean allInStock = itemDtos.stream().allMatch(CartItemDto::isInStock);

        return CartDto.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .items(itemDtos)
                .totalQuantity(totalQty)
                .subtotal(subtotal)
                .estimatedShipping(shippingFee)
                .totalAmount(total)
                .allItemsInStock(allInStock)
                .build();
    }
}

