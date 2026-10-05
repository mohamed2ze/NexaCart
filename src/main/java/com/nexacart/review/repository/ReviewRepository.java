package com.nexacart.review.repository;

import com.nexacart.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    @EntityGraph(attributePaths = {"user"})
    Page<Review> findByProductId(UUID productId, Pageable pageable);

    Optional<Review> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByProductIdAndUserId(UUID productId, UUID userId);

    @Query("""
            select coalesce(avg(r.rating), 0) as averageRating, count(r) as reviewCount
            from Review r where r.product.id = :productId
            """)
    RatingSummary summarize(@Param("productId") UUID productId);

    interface RatingSummary {
        Double getAverageRating();

        Long getReviewCount();
    }
}
