package com.nexacart.inventory.repository;

import com.nexacart.inventory.entity.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    @EntityGraph(attributePaths = {"product", "performedBy"})
    Page<InventoryTransaction> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);
}
