package com.nexacart.cart.dto;

import com.nexacart.product.dto.ProductResponse;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID productId,
        String name,
        String sku,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        int stockQuantity,
        boolean available,
        String productStatus
) {

    public static CartItemResponse from(ProductResponse product, int quantity, BigDecimal lineTotal) {
        return new CartItemResponse(
                product.id(),
                product.name(),
                product.sku(),
                product.price(),
                quantity,
                lineTotal,
                product.stockQuantity(),
                product.available(),
                product.status()
        );
    }
}
