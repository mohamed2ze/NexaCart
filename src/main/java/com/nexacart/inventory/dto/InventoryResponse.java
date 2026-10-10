package com.nexacart.inventory.dto;

import com.nexacart.inventory.entity.Inventory;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID productId,
        String sku,
        String productName,
        int quantity,
        int lowStockThreshold,
        boolean lowStock,
        Instant updatedAt
) {

    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getProduct().getId(),
                inventory.getProduct().getSku(),
                inventory.getProduct().getName(),
                inventory.getQuantity(),
                inventory.getLowStockThreshold(),
                inventory.isLowStock(),
                inventory.getUpdatedAt()
        );
    }
}
