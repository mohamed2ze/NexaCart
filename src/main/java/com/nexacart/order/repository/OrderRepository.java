package com.nexacart.order.repository;

import com.nexacart.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @Query("select o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    @Query("select o from Order o left join fetch o.items where o.id = :id and o.user.id = :userId")
    Optional<Order> findByIdAndUserIdWithItems(@Param("id") UUID id, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = {"user"})
    Page<Order> findAll(org.springframework.data.jpa.domain.Specification<Order> spec, Pageable pageable);
}
