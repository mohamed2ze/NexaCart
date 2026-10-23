package com.nexacart.cart;

import com.jayway.jsonpath.JsonPath;
import com.nexacart.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartAndAddressIntegrationTest extends IntegrationTestBase {

    @Test
    void firstAddressBecomesDefaultAndDefaultCanBeChanged() throws Exception {
        String customer = registerCustomer("addresses@example.com");
        MvcResult first = postJson("/api/v1/addresses", customer, Map.of(
                        "recipientName", "First Person",
                        "line1", "1 First Street",
                        "city", "Cairo",
                        "country", "Egypt",
                        "defaultAddress", false
                ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(true))
                .andReturn();
        String firstId = id(first);

        MvcResult second = postJson("/api/v1/addresses", customer, Map.of(
                        "recipientName", "Second Person",
                        "line1", "2 Second Street",
                        "city", "Alexandria",
                        "country", "Egypt",
                        "defaultAddress", false
                ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAddress").value(false))
                .andReturn();
        String secondId = id(second);

        patchJson("/api/v1/addresses/" + secondId + "/default", customer, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultAddress").value(true));

        getJson("/api/v1/addresses", customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondId))
                .andExpect(jsonPath("$[0].defaultAddress").value(true))
                .andExpect(jsonPath("$[1].id").value(firstId))
                .andExpect(jsonPath("$[1].defaultAddress").value(false));

        deleteJson("/api/v1/addresses/" + secondId, customer).andExpect(status().isNoContent());
        getJson("/api/v1/addresses/" + firstId, customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultAddress").value(true));
    }

    @Test
    void addressOwnershipIsEnforced() throws Exception {
        String owner = registerCustomer("address-owner@example.com");
        String other = registerCustomer("address-other@example.com");
        String addressId = createAddress(owner).toString();

        getJson("/api/v1/addresses/" + addressId, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        putJson("/api/v1/addresses/" + addressId, other, Map.of(
                        "recipientName", "Hijack",
                        "line1", "Malicious",
                        "city", "Nowhere",
                        "country", "Nowhere"
                ))
                .andExpect(status().isNotFound());
        deleteJson("/api/v1/addresses/" + addressId, other)
                .andExpect(status().isNotFound());
    }

    @Test
    void cartUsesServerPricesAndCalculatesTotals() throws Exception {
        String admin = createAdmin("cart-admin@example.com");
        UUID category = createCategory(admin, "Cart Category");
        UUID product = createProduct(admin, category, "CART-1", "Cart Product", "10.00", 20);
        String customer = registerCustomer("cart@example.com");

        MvcResult added = postJson("/api/v1/cart/items", customer, Map.of(
                        "productId", product.toString(),
                        "quantity", 2,
                        "unitPrice", "0.01",
                        "lineTotal", "0.02"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(10.00))
                .andExpect(jsonPath("$.items[0].lineTotal").value(20.00))
                .andExpect(jsonPath("$.checkoutReady").value(true))
                .andReturn();

        assertThat(decimal(added, "$.subtotal")).isEqualByComparingTo("20.00");
        assertThat(decimal(added, "$.estimatedShipping")).isEqualByComparingTo("5.99");
        assertThat(decimal(added, "$.estimatedTotal")).isEqualByComparingTo("25.99");

        patchJson("/api/v1/cart/items/" + product, customer, Map.of("quantity", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.subtotal").value(30.00));

        deleteJson("/api/v1/cart/items/" + product, customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.checkoutReady").value(false));
    }

    @Test
    void cartRejectsInvalidQuantitiesAndInsufficientStock() throws Exception {
        String admin = createAdmin("stock-admin@example.com");
        UUID category = createCategory(admin, "Stock Category");
        UUID product = createProduct(admin, category, "STOCK-1", "Scarce Product", "10.00", 2);
        String customer = registerCustomer("stock-customer@example.com");

        postJson("/api/v1/cart/items", customer, Map.of(
                        "productId", product.toString(),
                        "quantity", 0
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        postJson("/api/v1/cart/items", customer, Map.of(
                        "productId", product.toString(),
                        "quantity", 3
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void cartsAreIsolatedPerCustomer() throws Exception {
        String admin = createAdmin("isolation-admin@example.com");
        UUID category = createCategory(admin, "Isolation Category");
        UUID product = createProduct(admin, category, "ISO-1", "Isolated Product", "12.00", 10);
        String first = registerCustomer("isolation-one@example.com");
        String second = registerCustomer("isolation-two@example.com");

        addToCart(first, product, 2);
        getJson("/api/v1/cart", second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(0));

        deleteJson("/api/v1/cart", first).andExpect(status().isNoContent());
        getJson("/api/v1/cart", first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void inactiveProductsRemainVisibleButBlockCheckout() throws Exception {
        String admin = createAdmin("inactive-admin@example.com");
        UUID category = createCategory(admin, "Inactive Category");
        UUID product = createProduct(admin, category, "INA-1", "Soon Inactive", "15.00", 5);
        String customer = registerCustomer("inactive-customer@example.com");
        addToCart(customer, product, 1);

        deleteJson("/api/v1/admin/products/" + product, admin).andExpect(status().isNoContent());

        MvcResult cart = getJson("/api/v1/cart", customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].available").value(false))
                .andExpect(jsonPath("$.checkoutReady").value(false))
                .andReturn();
        assertThat((Boolean) JsonPath.read(cart.getResponse().getContentAsString(), "$.checkoutReady")).isFalse();

        deleteJson("/api/v1/cart/items/" + product, customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void freeShippingThresholdIsApplied() throws Exception {
        String admin = createAdmin("shipping-admin@example.com");
        UUID category = createCategory(admin, "Shipping Category");
        UUID product = createProduct(admin, category, "SHIP-1", "Expensive Product", "120.00", 5);
        String customer = registerCustomer("shipping-customer@example.com");

        MvcResult cart = postJson("/api/v1/cart/items", customer, Map.of(
                        "productId", product.toString(),
                        "quantity", 1
                ))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(decimal(cart, "$.estimatedShipping")).isEqualByComparingTo("0.00");
        assertThat(decimal(cart, "$.estimatedTotal")).isEqualByComparingTo("120.00");
    }
}
