package com.nexacart.review.service;

import com.nexacart.common.exception.ApiException;
import com.nexacart.common.response.PageResponse;
import com.nexacart.order.entity.OrderStatus;
import com.nexacart.order.repository.OrderItemRepository;
import com.nexacart.product.entity.Product;
import com.nexacart.product.repository.ProductRepository;
import com.nexacart.review.dto.ProductReviewsResponse;
import com.nexacart.review.dto.ReviewRequest;
import com.nexacart.review.dto.ReviewResponse;
import com.nexacart.review.entity.Review;
import com.nexacart.review.repository.ReviewRepository;
import com.nexacart.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class ReviewService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            ProductRepository productRepository,
            OrderItemRepository orderItemRepository,
            UserRepository userRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProductReviewsResponse listForProduct(UUID productId, int page, int size) {
        Product product = getPublicProduct(productId);
        Pageable pageable = pageable(page, size);
        Page<Review> reviews = reviewRepository.findByProductId(product.getId(), pageable);
        var summary = reviewRepository.summarize(productId);
        BigDecimal average = BigDecimal.valueOf(
                summary == null || summary.getAverageRating() == null ? 0.0 : summary.getAverageRating()
        ).setScale(2, RoundingMode.HALF_UP);
        long count = summary == null || summary.getReviewCount() == null ? 0L : summary.getReviewCount();
        return new ProductReviewsResponse(
                average,
                count,
                PageResponse.from(reviews.map(ReviewResponse::from))
        );
    }

    @Transactional
    public ReviewResponse create(UUID userId, UUID productId, ReviewRequest request) {
        Product product = getPublicProduct(productId);
        boolean verified = orderItemRepository.existsPurchaseWithStatus(
                userId,
                productId,
                OrderStatus.DELIVERED
        );
        if (!verified) {
            throw ApiException.forbidden(
                    "REVIEW_NOT_ELIGIBLE",
                    "Only customers with a delivered purchase of this product can review it"
            );
        }
        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw ApiException.conflict("REVIEW_ALREADY_EXISTS", "You have already reviewed this product");
        }
        Review review = new Review();
        review.setProduct(product);
        review.setUser(userRepository.getReferenceById(userId));
        review.setRating(request.rating());
        review.setTitle(trimToNull(request.title()));
        review.setComment(trimToNull(request.comment()));
        review.setVerifiedPurchase(true);
        return ReviewResponse.from(reviewRepository.saveAndFlush(review));
    }

    @Transactional
    public ReviewResponse update(UUID userId, UUID reviewId, ReviewRequest request) {
        Review review = reviewRepository.findByIdAndUserId(reviewId, userId)
                .orElseThrow(() -> ApiException.notFound("REVIEW_NOT_FOUND", "Review not found"));
        review.setRating(request.rating());
        review.setTitle(trimToNull(request.title()));
        review.setComment(trimToNull(request.comment()));
        return ReviewResponse.from(reviewRepository.saveAndFlush(review));
    }

    @Transactional
    public void delete(UUID userId, UUID reviewId) {
        Review review = reviewRepository.findByIdAndUserId(reviewId, userId)
                .orElseThrow(() -> ApiException.notFound("REVIEW_NOT_FOUND", "Review not found"));
        reviewRepository.delete(review);
    }

    private Product getPublicProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found"));
        if (!product.isPurchasable()) {
            throw ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found");
        }
        return product;
    }

    private Pageable pageable(int page, int size) {
        if (page < 0) {
            throw ApiException.badRequest("INVALID_PAGE", "page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("INVALID_PAGE_SIZE", "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
