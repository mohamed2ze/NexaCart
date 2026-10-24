package com.nexacart.payment;

import com.jayway.jsonpath.JsonPath;
import com.nexacart.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentIntegrationTest extends IntegrationTestBase {

    @Test
    void paymentCompletionIsIdempotentAndConfirmsOrder() throws Exception {
        Scenario scenario = createOrderScenario("payment");
        String paymentId = initiatePayment(scenario);
        assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(1);

        postJson("/api/v1/orders/" + scenario.orderId() + "/payments", scenario.customer(), Map.of())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId));
        assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(1);

        postJson("/api/v1/payments/" + paymentId + "/simulate", scenario.customer(), Map.of("outcome", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
        getJson("/api/v1/orders/" + scenario.orderId(), scenario.customer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        postJson("/api/v1/payments/" + paymentId + "/simulate", scenario.customer(), Map.of("outcome", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from payments", Integer.class)).isEqualTo(1);

        postJson("/api/v1/payments/" + paymentId + "/simulate", scenario.customer(), Map.of("outcome", "FAILURE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_FINALIZED"));

        getJson("/api/v1/orders/" + scenario.orderId() + "/payments", scenario.customer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].provider").value("SIMULATED"));
    }

    @Test
    void failedPaymentCanBeRetriedWithANewPayment() throws Exception {
        Scenario scenario = createOrderScenario("retry");
        String firstPaymentId = initiatePayment(scenario);

        postJson("/api/v1/payments/" + firstPaymentId + "/simulate", scenario.customer(), Map.of("outcome", "FAILURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").isNotEmpty());
        getJson("/api/v1/orders/" + scenario.orderId(), scenario.customer())
                .andExpect(jsonPath("$.status").value("PENDING"));

        MvcResult second = postJson(
                "/api/v1/orders/" + scenario.orderId() + "/payments",
                scenario.customer(),
                Map.of()
        )
                .andExpect(status().isCreated())
                .andReturn();
        String secondPaymentId = id(second);
        assertThat(secondPaymentId).isNotEqualTo(firstPaymentId);

        postJson("/api/v1/payments/" + secondPaymentId + "/simulate", scenario.customer(), Map.of("outcome", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        getJson("/api/v1/orders/" + scenario.orderId(), scenario.customer())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payments where order_id = ?",
                Integer.class,
                UUID.fromString(scenario.orderId())
        )).isEqualTo(2);
    }

    @Test
    void cancellingPaidOrderRefundsPaymentAndRestoresStock() throws Exception {
        Scenario scenario = createOrderScenario("refund");
        String paymentId = initiatePayment(scenario);
        postJson("/api/v1/payments/" + paymentId + "/simulate", scenario.customer(), Map.of("outcome", "SUCCESS"))
                .andExpect(status().isOk());

        postJson("/api/v1/orders/" + scenario.orderId() + "/cancel", scenario.customer(), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        getJson("/api/v1/payments/" + paymentId, scenario.customer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundReference").isNotEmpty());
        assertThat(stock(scenario.productId())).isEqualTo(10);
    }

    @Test
    void cancellingUnpaidOrderClosesPendingPayment() throws Exception {
        Scenario scenario = createOrderScenario("close");
        String paymentId = initiatePayment(scenario);
        postJson("/api/v1/orders/" + scenario.orderId() + "/cancel", scenario.customer(), Map.of())
                .andExpect(status().isOk());

        getJson("/api/v1/payments/" + paymentId, scenario.customer())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        postJson("/api/v1/payments/" + paymentId + "/simulate", scenario.customer(), Map.of("outcome", "SUCCESS"))
                .andExpect(status().isConflict());
    }

    @Test
    void paymentOwnershipIsEnforced() throws Exception {
        Scenario scenario = createOrderScenario("ownership");
        String paymentId = initiatePayment(scenario);
        String other = registerCustomer("payment-other@example.com");

        getJson("/api/v1/payments/" + paymentId, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        postJson("/api/v1/payments/" + paymentId + "/simulate", other, Map.of("outcome", "SUCCESS"))
                .andExpect(status().isNotFound());
    }

    private Scenario createOrderScenario(String prefix) throws Exception {
        String admin = createAdmin(prefix + "-admin@example.com");
        UUID category = createCategory(admin, prefix + " Category");
        UUID product = createProduct(admin, category, prefix.toUpperCase() + "-1", prefix + " Product", "20.00", 10);
        String customer = registerCustomer(prefix + "-customer@example.com");
        UUID address = createAddress(customer);
        addToCart(customer, product, 1);
        String orderId = id(placeOrder(customer, prefix + "-order-key", address));
        return new Scenario(customer, product, orderId);
    }

    private String initiatePayment(Scenario scenario) throws Exception {
        MvcResult result = postJson(
                "/api/v1/orders/" + scenario.orderId() + "/payments",
                scenario.customer(),
                Map.of()
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.provider").value("SIMULATED"))
                .andExpect(jsonPath("$.amount").value(25.99))
                .andReturn();
        String paymentId = id(result);
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.providerReference"))
                .startsWith("sim_");
        return paymentId;
    }

    private record Scenario(String customer, UUID productId, String orderId) {
    }
}
