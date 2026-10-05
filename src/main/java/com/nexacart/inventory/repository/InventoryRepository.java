package com.nexacart.inventory.repository;

import com.nexacart.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductId(UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from Inventory i
            join fetch i.product p
            join fetch p.category
            where p.id = :productId
            """)
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from Inventory i
            join fetch i.product p
            join fetch p.category
            where p.id in :productIds
            order by p.id
            """)
    List<Inventory> findAllByProductIdInForUpdate(@Param("productIds") Collection<UUID> productIds);

    @Query("""
            select i from Inventory i
            join fetch i.product p
            where i.quantity <= i.lowStockThreshold
            order by i.quantity asc, p.name asc
            """)
    List<Inventory> findLowStock();

    @Query("select i from Inventory i join fetch i.product p where i.product.id = :productId")
    Optional<Inventory> findWithProductByProductId(@Param("productId") UUID productId);
}
