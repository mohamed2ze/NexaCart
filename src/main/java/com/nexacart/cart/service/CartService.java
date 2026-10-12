package com.nexacart.cart.service;

import com.nexacart.cart.dto.AddCartItemRequest;
import com.nexacart.cart.dto.CartItemResponse;
import com.nexacart.cart.dto.CartResponse;
import com.nexacart.cart.dto.UpdateCartItemRequest;
import com.nexacart.cart.entity.Cart;
import com.nexacart.cart.entity.CartItem;
import com.nexacart.cart.repository.CartItemRepository;
import com.nexacart.cart.repository.CartRepository;
import com.nexacart.common.exception.ApiException;
import com.nexacart.common.pricing.PricingService;
import com.nexacart.inventory.entity.Inventory;
import com.nexacart.product.dto.ProductResponse;
import com.nexacart.product.entity.Product;
import com.nexacart.product.repository.ProductRepository;
import com.nexacart.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {

    private static final int MAX_ITEM_QUANTITY = 1000;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PricingService pricingService;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            PricingService pricingService
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.pricingService = pricingService;
    }

    @Transactional
    public CartResponse getCart(UUID userId) {
        Cart cart = getOrCreateCart(userId);
        return buildResponse(cart);
    }

    @Transactional
    public CartResponse addItem(UUID userId, AddCartItemRequest request) {
        Cart cart = lockOrCreateCart(userId);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found"));
        requirePurchasable(product);
        int availableStock = stockOf(product);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(null);
        int newQuantity = request.quantity();
        if (item != null) {
            newQuantity = item.getQuantity() + request.quantity();
        }
        if (newQuantity > MAX_ITEM_QUANTITY) {
            throw ApiException.badRequest(
                    "QUANTITY_LIMIT_EXCEEDED",
                    "A maximum of " + MAX_ITEM_QUANTITY + " units per product is allowed"
            );
        }
        if (newQuantity > availableStock) {
            throw ApiException.conflict(
                    "INSUFFICIENT_STOCK",
                    "Only " + availableStock + " unit(s) of " + product.getName() + " are available"
            );
        }
        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
        }
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);
        return buildResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(UUID userId, UUID productId, UpdateCartItemRequest request) {
        Cart cart = lockCart(userId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> ApiException.notFound("CART_ITEM_NOT_FOUND", "Product is not in the cart"));
        Product product = item.getProduct();
        requirePurchasable(product);
        int availableStock = stockOf(product);
        if (request.quantity() > availableStock) {
            throw ApiException.conflict(
                    "INSUFFICIENT_STOCK",
                    "Only " + availableStock + " unit(s) of " + product.getName() + " are available"
            );
        }
        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        return buildResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(UUID userId, UUID productId) {
        Cart cart = lockCart(userId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> ApiException.notFound("CART_ITEM_NOT_FOUND", "Product is not in the cart"));
        cartItemRepository.delete(item);
        return buildResponse(cart);
    }

    @Transactional
    public CartResponse clear(UUID userId) {
        Cart cart = lockOrCreateCart(userId);
        cartItemRepository.deleteAllByCartId(cart.getId());
        return emptyResponse(cart);
    }

    private CartResponse buildResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        List<CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItems = 0;
        boolean checkoutReady = !items.isEmpty();

        for (CartItem item : items) {
            ProductResponse productResponse = ProductResponse.from(item.getProduct());
            BigDecimal lineTotal = pricingService.lineTotal(item.getProduct().getPrice(), item.getQuantity());
            subtotal = subtotal.add(lineTotal);
            totalItems += item.getQuantity();
            itemResponses.add(CartItemResponse.from(productResponse, item.getQuantity(), lineTotal));
            if (!productResponse.available() || item.getQuantity() > productResponse.stockQuantity()) {
                checkoutReady = false;
            }
        }
        subtotal = pricingService.normalize(subtotal);
        BigDecimal shipping = pricingService.shippingFor(subtotal);
        BigDecimal estimatedTotal = pricingService.total(subtotal, shipping, BigDecimal.ZERO);
        return new CartResponse(
                cart.getId(),
                itemResponses,
                totalItems,
                subtotal,
                shipping,
                estimatedTotal,
                pricingService.currency(),
                checkoutReady
        );
    }

    private CartResponse emptyResponse(Cart cart) {
        return new CartResponse(
                cart.getId(),
                List.of(),
                0,
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                pricingService.currency(),
                false
        );
    }

    private void requirePurchasable(Product product) {
        if (!product.isPurchasable()) {
            throw ApiException.conflict("PRODUCT_UNAVAILABLE", "Product is not available for purchase");
        }
    }

    private int stockOf(Product product) {
        Inventory inventory = product.getInventory();
        return inventory == null ? 0 : inventory.getQuantity();
    }

    private Cart lockCart(UUID userId) {
        return cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> ApiException.notFound("CART_NOT_FOUND", "Cart not found"));
    }

    private Cart lockOrCreateCart(UUID userId) {
        return cartRepository.findByUserIdForUpdate(userId).orElseGet(() -> getOrCreateCart(userId));
    }

    private Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(userRepository.getReferenceById(userId));
            return cartRepository.save(cart);
        });
    }
}
