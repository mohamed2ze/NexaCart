package com.nexacart.order;

import com.jayway.jsonpath.JsonPath;
import com.nexacart.common.exception.ApiException;
import com.nexacart.order.dto.CreateOrderRequest;
import com.nexacart.order.dto.OrderResponse;
import com.nexacart.order.service.OrderService;
import com.nexacart.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderIntegrationTest extends IntegrationTestBase {

    @Autowired
    private OrderService orderService;

    @Test
    void createsOrderWithSnapshotsAndDeductsInventory() throws Exception {
        String admin = createAdmin("order-admin@example.com");
        UUID category = createCategory(admin, "Order Category");
        UUID product = createProduct(admin, category, "ORDER-1", "Order Product", "10.00", 10);
        String customer = registerCustomer("order-customer@example.com");
        UUID address = createAddress(customer);
        addToCart(customer, product, 2);

        MvcResult created = placeOrder(customer, "order-key-1", address);
        String orderId = id(created);

        assertThat((String) JsonPath.read(created.getResponse().getContentAsString(), "$.status"))
                .isEqualTo("PENDING");
        assertThat((String) JsonPath.read(created.getResponse().getContentAsString(), "$.items[0].sku"))
                .isEqualTo("ORDER-1");
        assertThat((Integer) JsonPath.read(created.getResponse().getContentAsString(), "$.items[0].quantity"))
                .isEqualTo(2);
        assertThat(decimal(created, "$.subtotal")).isEqualByComparingTo("20.00");
        assertThat(decimal(created, "$.shippingCost")).isEqualByComparingTo("5.99");
        assertThat(decimal(created, "$.discountAmount")).isEqualByComparingTo("0.00");
        assertThat(decimal(created, "$.totalAmount")).isEqualByComparingTo("25.99");
        assertThat((String) JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.shippingAddress.city"
        )).isEqualTo("Cairo");

        assertThat(stock(product)).isEqualTo(8);
        getJson("/api/v1/cart", customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        putJson("/api/v1/admin/products/" + product, admin, Map.of(
                        "categoryId", category.toString(),
                        "name", "Order Product",
                        "sku", "ORDER-1",
                        "description", "Price changed later",
                        "price", "99.99",
                        "status", "ACTIVE"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(99.99));

        getJson("/api/v1/orders/" + orderId, customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(10.00))
                .andExpect(jsonPath("$.totalAmount").value(25.99));

        putJson("/api/v1/addresses/" + address, customer, Map.of(
                        "recipientName", "Changed Recipient",
                        "line1", "999 New Street",
                        "city", "Alexandria",
                        "country", "Egypt",
                        "defaultAddress", true
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line1").value("999 New Street"));
        getJson("/api/v1/orders/" + orderId, customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingAddress.recipientName").value("Test Recipient"))
                .andExpect(jsonPath("$.shippingAddress.line1").value("10 Test Street"))
                .andExpect(jsonPath("$.shippingAddress.city").value("Cairo"));

        MvcResult retried = placeOrder(customer, "order-key-1", address);
        assertThat(id(retried)).isEqualTo(orderId);
        assertThat(stock(product)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from inventory_transactions where transaction_type = 'ORDER_DEDUCTION'",
                Integer.class
        )).isEqualTo(1);

        postOrder(customer, "different-key", Map.of("addressId", address.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
    }

    @Test
    void concurrentIdenticalRequestsCreateOneOrder() throws Exception {
        String admin = createAdmin("idempotent-admin@example.com");
        UUID category = createCategory(admin, "Idempotent Category");
        UUID product = createProduct(admin, category, "IDEM-1", "Idempotent Product", "15.00", 10);
        String customer = registerCustomer("idempotent-customer@example.com");
        UUID customerId = userId(customer);
        UUID address = createAddress(customer);
        addToCart(customer, product, 1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<OrderResponse> task = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return orderService.createOrder(
                        customerId,
                        "concurrent-idempotency-key",
                        new CreateOrderRequest(address)
                );
            };
            Future<OrderResponse> first = executor.submit(task);
            Future<OrderResponse> second = executor.submit(task);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            OrderResponse firstOrder = first.get(20, TimeUnit.SECONDS);
            OrderResponse secondOrder = second.get(20, TimeUnit.SECONDS);
            assertThat(firstOrder.id()).isEqualTo(secondOrder.id());
        } finally {
            executor.shutdownNow();
        }

        assertThat(stock(product)).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from inventory_transactions where transaction_type = 'ORDER_DEDUCTION'",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void concurrentOrdersCannotOversellLastUnit() throws Exception {
        String admin = createAdmin("oversell-admin@example.com");
        UUID category = createCategory(admin, "Oversell Category");
        UUID product = createProduct(admin, category, "LAST-1", "Last Unit", "25.00", 1);
        String firstCustomer = registerCustomer("oversell-one@example.com");
        String secondCustomer = registerCustomer("oversell-two@example.com");
        UUID firstId = userId(firstCustomer);
        UUID secondId = userId(secondCustomer);
        UUID firstAddress = createAddress(firstCustomer);
        UUID secondAddress = createAddress(secondCustomer);
        addToCart(firstCustomer, product, 1);
        addToCart(secondCustomer, product, 1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<Attempt> firstTask = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    return new Attempt(orderService.createOrder(
                            firstId,
                            "oversell-first",
                            new CreateOrderRequest(firstAddress)
                    ), null);
                } catch (Throwable error) {
                    return new Attempt(null, error);
                }
            };
            java.util.concurrent.Callable<Attempt> secondTask = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    return new Attempt(orderService.createOrder(
                            secondId,
                            "oversell-second",
                            new CreateOrderRequest(secondAddress)
                    ), null);
                } catch (Throwable error) {
                    return new Attempt(null, error);
                }
            };
            Future<Attempt> first = executor.submit(firstTask);
            Future<Attempt> second = executor.submit(secondTask);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Attempt firstAttempt = first.get(20, TimeUnit.SECONDS);
            Attempt secondAttempt = second.get(20, TimeUnit.SECONDS);
            long successes = java.util.stream.Stream.of(firstAttempt, secondAttempt)
                    .filter(attempt -> attempt.order() != null)
                    .count();
            assertThat(successes).isEqualTo(1);
            Throwable failure = firstAttempt.order() == null ? firstAttempt.error() : secondAttempt.error();
            assertThat(failure).isInstanceOf(ApiException.class);
            assertThat(((ApiException) failure).getCode()).isEqualTo("INSUFFICIENT_STOCK");
        } finally {
            executor.shutdownNow();
        }

        assertThat(stock(product)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from inventory_transactions where transaction_type = 'ORDER_DEDUCTION'",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void cancellationRestoresStockExactlyOnce() throws Exception {
        String admin = createAdmin("cancel-admin@example.com");
        UUID category = createCategory(admin, "Cancel Category");
        UUID product = createProduct(admin, category, "CANCEL-1", "Cancel Product", "30.00", 10);
        String customer = registerCustomer("cancel-customer@example.com");
        UUID address = createAddress(customer);
        addToCart(customer, product, 2);
        String orderId = id(placeOrder(customer, "cancel-key", address));
        assertThat(stock(product)).isEqualTo(8);

        postJson("/api/v1/orders/" + orderId + "/cancel", customer, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
        assertThat(stock(product)).isEqualTo(10);

        postJson("/api/v1/orders/" + orderId + "/cancel", customer, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stock(product)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from inventory_transactions where transaction_type = 'ORDER_RESTOCK'",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void customerOwnershipAndAdminAccessAreEnforced() throws Exception {
        String admin = createAdmin("ownership-admin@example.com");
        UUID category = createCategory(admin, "Ownership Category");
        UUID product = createProduct(admin, category, "OWN-1", "Ownership Product", "40.00", 5);
        String owner = registerCustomer("order-owner@example.com");
        String other = registerCustomer("order-other@example.com");
        UUID address = createAddress(owner);
        addToCart(owner, product, 1);
        String orderId = id(placeOrder(owner, "ownership-key", address));

        getJson("/api/v1/orders/" + orderId, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        postJson("/api/v1/orders/" + orderId + "/cancel", other, Map.of())
                .andExpect(status().isNotFound());

        getJson("/api/v1/admin/orders/" + orderId, admin)
                .andExpect(status().isOk());
        getJson("/api/v1/admin/orders", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerEmail").value("order-owner@example.com"));
        getJson("/api/v1/admin/orders", owner)
                .andExpect(status().isForbidden());
    }

    @Test
    void orderStatusTransitionsAreValidated() throws Exception {
        String admin = createAdmin("transition-admin@example.com");
        UUID category = createCategory(admin, "Transition Category");
        UUID product = createProduct(admin, category, "TRANS-1", "Transition Product", "50.00", 5);
        String customer = registerCustomer("transition-customer@example.com");
        UUID address = createAddress(customer);
        addToCart(customer, product, 1);
        String orderId = id(placeOrder(customer, "transition-key", address));

        patchJson("/api/v1/admin/orders/" + orderId + "/status", admin, Map.of("status", "SHIPPED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"));

        patchJson("/api/v1/admin/orders/" + orderId + "/status", admin, Map.of("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        patchJson("/api/v1/admin/orders/" + orderId + "/status", admin, Map.of("status", "PROCESSING"))
                .andExpect(status().isOk());
        patchJson("/api/v1/admin/orders/" + orderId + "/status", admin, Map.of("status", "SHIPPED"))
                .andExpect(status().isOk());
        patchJson("/api/v1/admin/orders/" + orderId + "/status", admin, Map.of("status", "DELIVERED"))
                .andExpect(status().isOk());

        postJson("/api/v1/orders/" + orderId + "/cancel", customer, Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        patchJson("/api/v1/admin/orders/" + orderId + "/status", admin, Map.of("status", "CANCELLED"))
                .andExpect(status().isConflict());
        assertThat(stock(product)).isEqualTo(4);
    }

    @Test
    void checkoutValidationLeavesDataUnchanged() throws Exception {
        String admin = createAdmin("validation-admin@example.com");
        UUID category = createCategory(admin, "Validation Category");
        UUID product = createProduct(admin, category, "VAL-1", "Validation Product", "20.00", 5);
        String customer = registerCustomer("validation-customer@example.com");
        UUID address = createAddress(customer);

        postOrder(customer, "empty-cart", Map.of("addressId", address.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));

        addToCart(customer, product, 2);
        postOrder(customer, "missing-address", Map.of("addressId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        assertThat(stock(product)).isEqualTo(5);

        patchJson("/api/v1/admin/inventory/" + product + "/stock", admin, Map.of(
                        "delta", -4,
                        "reason", "Reduce available stock for test"
                ))
                .andExpect(status().isOk());
        assertThat(stock(product)).isEqualTo(1);

        postOrder(customer, "insufficient", Map.of("addressId", address.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(stock(product)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isZero();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType("application/json")
                        .content(json(Map.of("addressId", address.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private record Attempt(OrderResponse order, Throwable error) {
    }
}
