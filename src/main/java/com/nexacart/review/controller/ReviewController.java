package com.nexacart.review.controller;

import com.nexacart.review.dto.ProductReviewsResponse;
import com.nexacart.review.dto.ReviewRequest;
import com.nexacart.review.dto.ReviewResponse;
import com.nexacart.review.service.ReviewService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reviews")
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/products/{productId}/reviews")
    public ProductReviewsResponse list(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return reviewService.listForProduct(productId, page, size);
    }

    @PostMapping("/products/{productId}/reviews")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "201", description = "Review created")
    @ApiResponse(responseCode = "403", description = "No delivered purchase for this product")
    public ResponseEntity<ReviewResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody ReviewRequest request
    ) {
        ReviewResponse created = reviewService.create(UUID.fromString(jwt.getSubject()), productId, request);
        return ResponseEntity.created(URI.create("/api/v1/reviews/" + created.id())).body(created);
    }

    @PatchMapping("/reviews/{reviewId}")
    @SecurityRequirement(name = "bearerAuth")
    public ReviewResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return reviewService.update(UUID.fromString(jwt.getSubject()), reviewId, request);
    }

    @DeleteMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reviewId) {
        reviewService.delete(UUID.fromString(jwt.getSubject()), reviewId);
    }
}
