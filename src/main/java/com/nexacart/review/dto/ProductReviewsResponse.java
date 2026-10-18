package com.nexacart.review.dto;

import com.nexacart.common.response.PageResponse;

import java.math.BigDecimal;

public record ProductReviewsResponse(
        BigDecimal averageRating,
        long reviewCount,
        PageResponse<ReviewResponse> reviews
) {
}
