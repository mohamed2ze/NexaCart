package com.nexacart.order.dto;

import com.nexacart.order.entity.Order;

public record ShippingAddressResponse(
        String recipientName,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country,
        String phone
) {

    public static ShippingAddressResponse from(Order order) {
        return new ShippingAddressResponse(
                order.getRecipientName(),
                order.getShippingLine1(),
                order.getShippingLine2(),
                order.getShippingCity(),
                order.getShippingState(),
                order.getShippingPostalCode(),
                order.getShippingCountry(),
                order.getShippingPhone()
        );
    }
}
