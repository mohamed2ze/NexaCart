package com.nexacart.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdjustStockRequest(
        Integer delta,
        Integer quantity,
        Integer lowStockThreshold,
        @NotBlank @Size(max = 500) String reason
) {
}
