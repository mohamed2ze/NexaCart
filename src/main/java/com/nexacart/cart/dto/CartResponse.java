package com.nexacart.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        List<CartItemResponse> items,
        int totalItems,
        BigDecimal subtotal,
        BigDecimal estimatedShipping,
        BigDecimal estimatedTotal,
        String currency,
        boolean checkoutReady
) {
}
