package com.nexacart.order.dto;

import com.nexacart.order.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminOrderSummaryResponse(
        UUID id,
        UUID customerId,
        String customerEmail,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt
) {

    public static AdminOrderSummaryResponse from(Order order) {
        return new AdminOrderSummaryResponse(
                order.getId(),
                order.getUser().getId(),
                order.getUser().getEmail(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt()
        );
    }
}
