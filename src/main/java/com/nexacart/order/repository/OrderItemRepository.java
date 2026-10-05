package com.nexacart.order.repository;

import com.nexacart.order.entity.OrderItem;
import com.nexacart.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Query("""
            select count(oi) > 0 from OrderItem oi
            where oi.product.id = :productId
              and oi.order.user.id = :userId
              and oi.order.status = :status
            """)
    boolean existsPurchaseWithStatus(
            @Param("userId") UUID userId,
            @Param("productId") UUID productId,
            @Param("status") OrderStatus status
    );
}
