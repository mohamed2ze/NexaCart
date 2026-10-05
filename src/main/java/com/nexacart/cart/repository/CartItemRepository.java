package com.nexacart.cart.repository;

import com.nexacart.cart.entity.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    @EntityGraph(attributePaths = {"product", "product.category", "product.inventory"})
    List<CartItem> findByCartIdOrderByCreatedAtAsc(UUID cartId);

    @Query("""
            select i from CartItem i
            where i.cart.id = :cartId
            order by i.createdAt asc
            """)
    List<CartItem> findForCheckout(@Param("cartId") UUID cartId);

    @EntityGraph(attributePaths = {"product", "product.category", "product.inventory"})
    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

    @Modifying
    @Query("delete from CartItem i where i.cart.id = :cartId")
    int deleteAllByCartId(@Param("cartId") UUID cartId);
}
