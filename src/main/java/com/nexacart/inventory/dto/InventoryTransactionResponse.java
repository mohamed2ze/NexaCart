package com.nexacart.inventory.dto;

import com.nexacart.inventory.entity.InventoryTransaction;

import java.time.Instant;
import java.util.UUID;

public record InventoryTransactionResponse(
        UUID id,
        UUID productId,
        String sku,
        String transactionType,
        int delta,
        int previousQuantity,
        int newQuantity,
        String reason,
        UUID referenceId,
        String performedBy,
        Instant createdAt
) {

    public static InventoryTransactionResponse from(InventoryTransaction transaction) {
        return new InventoryTransactionResponse(
                transaction.getId(),
                transaction.getProduct().getId(),
                transaction.getProduct().getSku(),
                transaction.getTransactionType().name(),
                transaction.getDelta(),
                transaction.getPreviousQuantity(),
                transaction.getNewQuantity(),
                transaction.getReason(),
                transaction.getReferenceId(),
                transaction.getPerformedBy() == null ? null : transaction.getPerformedBy().getEmail(),
                transaction.getCreatedAt()
        );
    }
}
