package com.nexacart.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 150) String recipientName,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 30) String postalCode,
        @NotBlank @Size(max = 100) String country,
        @Size(max = 30) String phone,
        Boolean defaultAddress
) {
}
