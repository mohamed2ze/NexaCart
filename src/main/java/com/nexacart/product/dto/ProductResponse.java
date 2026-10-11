package com.nexacart.product.dto;

import com.nexacart.inventory.entity.Inventory;
import com.nexacart.product.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String slug,
        String sku,
        String description,
        BigDecimal price,
        String status,
        int stockQuantity,
        boolean available,
        boolean lowStock,
        CategorySummary category,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProductResponse from(Product product) {
        Inventory inventory = product.getInventory();
        int stock = inventory == null ? 0 : inventory.getQuantity();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getSku(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus().name(),
                stock,
                product.isPurchasable() && stock > 0,
                inventory != null && inventory.isLowStock(),
                CategorySummary.from(product.getCategory()),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
