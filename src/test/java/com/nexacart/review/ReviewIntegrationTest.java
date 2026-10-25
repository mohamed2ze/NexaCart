package com.nexacart.review;

import com.nexacart.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewIntegrationTest extends IntegrationTestBase {

    @Test
    void onlyVerifiedPurchasersCanReviewAndAverageIsRecalculated() throws Exception {
        String admin = createAdmin("review-admin@example.com");
        UUID category = createCategory(admin, "Reviewed Category");
        UUID product = createProduct(admin, category, "REV-1", "Reviewed Product", "35.00", 10);
        String buyer = registerCustomer("review-buyer@example.com");
        String other = registerCustomer("review-other@example.com");
        UUID address = createAddress(buyer);
        addToCart(buyer, product, 1);
        String orderId = id(placeOrder(buyer, "review-order-key", address));

        postJson("/api/v1/products/" + product + "/reviews", buyer, Map.of(
                        "rating", 5,
                        "title", "Not delivered yet",
                        "comment", "Premature"
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ELIGIBLE"));

        moveToDelivered(admin, orderId);

        postJson("/api/v1/products/" + product + "/reviews", other, Map.of(
                        "rating", 1,
                        "title", "Fake review"
                ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ELIGIBLE"));

        postJson("/api/v1/products/" + product + "/reviews", buyer, Map.of(
                        "rating", 6,
                        "title", "Invalid"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        var created = postJson("/api/v1/products/" + product + "/reviews", buyer, Map.of(
                        "rating", 5,
                        "title", "Excellent",
                        "comment", "Works exactly as described"
                ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verifiedPurchase").value(true))
                .andExpect(jsonPath("$.authorName").value("Test Customer"))
                .andExpect(jsonPath("$.rating").value(5))
                .andReturn();
        String reviewId = id(created);

        postJson("/api/v1/products/" + product + "/reviews", buyer, Map.of("rating", 4))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

        getJson("/api/v1/products/" + product + "/reviews", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.00))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.reviews.content[0].id").value(reviewId));

        patchJson("/api/v1/reviews/" + reviewId, buyer, Map.of(
                        "rating", 3,
                        "title", "Changed my mind",
                        "comment", "Still useful"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(3));
        getJson("/api/v1/products/" + product + "/reviews", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(3.00));

        patchJson("/api/v1/reviews/" + reviewId, other, Map.of("rating", 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
        deleteJson("/api/v1/reviews/" + reviewId, other)
                .andExpect(status().isNotFound());

        deleteJson("/api/v1/reviews/" + reviewId, buyer)
                .andExpect(status().isNoContent());
        getJson("/api/v1/products/" + product + "/reviews", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.averageRating").value(0.00));
    }

    private void moveToDelivered(String admin, String orderId) throws Exception {
        for (String status : new String[]{"CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED"}) {
            patchJson("/api/v1/admin/orders/" + orderId + "/status", admin, Map.of("status", status))
                    .andExpect(status().isOk());
        }
    }
}
