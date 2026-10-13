package com.nexacart.address.dto;

import com.nexacart.address.entity.Address;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String recipientName,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country,
        String phone,
        boolean defaultAddress,
        Instant createdAt,
        Instant updatedAt
) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.getPhone(),
                address.isDefaultAddress(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}
