package com.nexacart.inventory.service;

import com.nexacart.common.exception.ApiException;
import com.nexacart.common.response.PageResponse;
import com.nexacart.inventory.dto.AdjustStockRequest;
import com.nexacart.inventory.dto.InventoryResponse;
import com.nexacart.inventory.dto.InventoryTransactionResponse;
import com.nexacart.inventory.entity.Inventory;
import com.nexacart.inventory.entity.InventoryTransaction;
import com.nexacart.inventory.entity.InventoryTransactionType;
import com.nexacart.inventory.repository.InventoryRepository;
import com.nexacart.inventory.repository.InventoryTransactionRepository;
import com.nexacart.product.entity.Product;
import com.nexacart.user.entity.User;
import com.nexacart.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryTransactionRepository transactionRepository,
            UserRepository userRepository
    ) {
        this.inventoryRepository = inventoryRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Inventory initialize(Product product, int initialQuantity, int lowStockThreshold, UUID actorId) {
        if (initialQuantity < 0 || lowStockThreshold < 0) {
            throw ApiException.badRequest("INVALID_STOCK", "Stock quantities cannot be negative");
        }
        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setQuantity(initialQuantity);
        inventory.setLowStockThreshold(lowStockThreshold);
        Inventory saved = inventoryRepository.save(inventory);
        recordTransaction(
                product,
                InventoryTransactionType.INITIALIZATION,
                initialQuantity,
                0,
                initialQuantity,
                "Initial stock setup",
                null,
                actorId
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(UUID productId) {
        Inventory inventory = inventoryRepository.findWithProductByProductId(productId)
                .orElseThrow(() -> ApiException.notFound("INVENTORY_NOT_FOUND", "Inventory not found for product"));
        return InventoryResponse.from(inventory);
    }

    @Transactional
    public InventoryResponse adjustStock(UUID actorId, UUID productId, AdjustStockRequest request) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> ApiException.notFound("INVENTORY_NOT_FOUND", "Inventory not found for product"));

        boolean hasDelta = request.delta() != null;
        boolean hasQuantity = request.quantity() != null;
        if (hasDelta == hasQuantity) {
            throw ApiException.badRequest(
                    "INVALID_STOCK_ADJUSTMENT",
                    "Exactly one of delta or quantity must be provided"
            );
        }
        if (request.delta() != null && request.delta() == 0) {
            throw ApiException.badRequest("INVALID_STOCK_ADJUSTMENT", "delta must not be zero");
        }
        if (request.quantity() != null && request.quantity() < 0) {
            throw ApiException.badRequest("INVALID_STOCK_ADJUSTMENT", "quantity cannot be negative");
        }
        if (request.lowStockThreshold() != null && request.lowStockThreshold() < 0) {
            throw ApiException.badRequest("INVALID_STOCK_ADJUSTMENT", "lowStockThreshold cannot be negative");
        }

        int previousQuantity = inventory.getQuantity();
        int newQuantity = hasDelta ? previousQuantity + request.delta() : request.quantity();
        if (newQuantity < 0) {
            throw ApiException.conflict("NEGATIVE_STOCK", "Stock cannot be reduced below zero");
        }
        boolean stockChanged = newQuantity != previousQuantity;
        boolean thresholdChanged = request.lowStockThreshold() != null
                && request.lowStockThreshold() != inventory.getLowStockThreshold();
        if (!stockChanged && !thresholdChanged) {
            throw ApiException.badRequest("NO_STOCK_CHANGE", "The adjustment would not change inventory");
        }

        inventory.setQuantity(newQuantity);
        if (request.lowStockThreshold() != null) {
            inventory.setLowStockThreshold(request.lowStockThreshold());
        }
        inventoryRepository.save(inventory);

        if (stockChanged) {
            recordTransaction(
                    inventory.getProduct(),
                    InventoryTransactionType.ADMIN_ADJUSTMENT,
                    newQuantity - previousQuantity,
                    previousQuantity,
                    newQuantity,
                    request.reason().trim(),
                    null,
                    actorId
            );
        }
        return InventoryResponse.from(inventory);
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> lowStock() {
        return inventoryRepository.findLowStock().stream().map(InventoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> history(UUID productId, int page, int size) {
        inventoryRepository.findWithProductByProductId(productId)
                .orElseThrow(() -> ApiException.notFound("INVENTORY_NOT_FOUND", "Inventory not found for product"));
        var transactions = transactionRepository.findByProductIdOrderByCreatedAtDesc(
                productId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.from(transactions.map(InventoryTransactionResponse::from));
    }

    private void recordTransaction(
            Product product,
            InventoryTransactionType type,
            int delta,
            int previousQuantity,
            int newQuantity,
            String reason,
            UUID referenceId,
            UUID actorId
    ) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setProduct(product);
        transaction.setTransactionType(type);
        transaction.setDelta(delta);
        transaction.setPreviousQuantity(previousQuantity);
        transaction.setNewQuantity(newQuantity);
        transaction.setReason(reason);
        transaction.setReferenceId(referenceId);
        if (actorId != null) {
            User actor = userRepository.getReferenceById(actorId);
            transaction.setPerformedBy(actor);
        }
        transactionRepository.save(transaction);
    }
}
