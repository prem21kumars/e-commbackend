package com.ecommerce.cart;

import com.ecommerce.modules.auth.entity.User;
import com.ecommerce.modules.auth.repository.UserRepository;
import com.ecommerce.modules.cart.dto.AddCartItemRequest;
import com.ecommerce.modules.cart.dto.CartDto;
import com.ecommerce.modules.cart.entity.Cart;
import com.ecommerce.modules.cart.entity.CartItem;
import com.ecommerce.modules.cart.repository.CartItemRepository;
import com.ecommerce.modules.cart.repository.CartRepository;
import com.ecommerce.modules.inventory.entity.Inventory;
import com.ecommerce.modules.product.entity.Product;
import com.ecommerce.modules.product.entity.ProductStatus;
import com.ecommerce.modules.product.repository.ProductRepository;
import com.ecommerce.modules.cart.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService Unit Tests")
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartService cartService;

    private User user;
    private Cart cart;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("user@example.com").build();
        cart = Cart.builder().id(10L).user(user).items(new ArrayList<>()).build();

        product = Product.builder()
                .id(100L)
                .name("Lego Set")
                .slug("lego-set")
                .sku("LEGO-100")
                .basePrice(new BigDecimal("1200.00"))
                .salePrice(new BigDecimal("999.00")) // Effective price = 999.00
                .status(ProductStatus.PUBLISHED)
                .inventory(Inventory.builder().quantityAvailable(50).quantityReserved(0).build())
                .images(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Add item to cart calculates authoritative price and free shipping over INR 999")
    void testAddItemCalculatesAuthoritativePriceAndShipping() {
        when(cartRepository.findByUserIdWithItems(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(10L, 100L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(i -> i.getArgument(0));

        AddCartItemRequest req = AddCartItemRequest.builder()
                .productId(100L)
                .quantity(1)
                .build();

        CartDto res = cartService.addItem(1L, req);

        assertNotNull(res);
        assertEquals(1, res.getItems().size());
        assertEquals(new BigDecimal("999.00"), res.getSubtotal());
        // INR 999 qualifies for free shipping
        assertEquals(BigDecimal.ZERO, res.getEstimatedShipping());
        assertEquals(new BigDecimal("999.00"), res.getTotalAmount());
        assertTrue(res.isAllItemsInStock());

        verify(cartItemRepository).save(any(CartItem.class));
    }
}

