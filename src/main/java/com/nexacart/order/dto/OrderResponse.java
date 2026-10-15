package com.nexacart.order.dto;

import com.nexacart.order.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        String status,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String currency,
        ShippingAddressResponse shippingAddress,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getStatus().name(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getSubtotal(),
                order.getShippingCost(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getCurrency(),
                ShippingAddressResponse.from(order),
                order.getCancelledAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
