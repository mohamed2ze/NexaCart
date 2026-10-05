package com.nexacart.order.repository;

import com.nexacart.order.entity.Order;
import com.nexacart.order.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> userId(UUID userId) {
        if (userId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Order> status(OrderStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
