package com.nexacart.payment.repository;

import com.nexacart.payment.entity.Payment;
import com.nexacart.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query("select p from Payment p join fetch p.order where p.id = :id")
    Optional<Payment> findByIdWithOrder(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(UUID orderId, PaymentStatus status);

    @Query("select p from Payment p where p.order.id = :orderId order by p.createdAt desc")
    List<Payment> findByOrderIdOrderByCreatedAtDesc(@Param("orderId") UUID orderId);
}
