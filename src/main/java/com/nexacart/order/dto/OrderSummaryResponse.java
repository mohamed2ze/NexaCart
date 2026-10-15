package com.nexacart.order.dto;

import com.nexacart.order.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        String status,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getStatus().name(),
                order.getSubtotal(),
                order.getShippingCost(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt()
        );
    }
}
