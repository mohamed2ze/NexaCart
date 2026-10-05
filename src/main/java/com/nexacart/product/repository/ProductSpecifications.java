package com.nexacart.product.repository;

import com.nexacart.product.entity.Product;
import com.nexacart.product.entity.ProductStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.UUID;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> publiclyVisible() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ProductStatus.ACTIVE),
                cb.isTrue(root.get("category").get("active"))
        );
    }

    public static Specification<Product> search(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(root.get("sku")), pattern)
        );
    }

    public static Specification<Product> categoryId(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> minPrice(BigDecimal minPrice) {
        if (minPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> maxPrice(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> available(Boolean available) {
        if (available == null) {
            return null;
        }
        if (available) {
            return (root, query, cb) -> cb.greaterThan(root.get("inventory").get("quantity"), 0);
        }
        return (root, query, cb) -> {
            var inventory = root.join("inventory", JoinType.LEFT);
            return cb.or(cb.isNull(inventory), cb.lessThanOrEqualTo(inventory.get("quantity"), 0));
        };
    }

    public static Specification<Product> status(ProductStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
