package com.nexacart.common;

import com.nexacart.common.pricing.PricingService;
import com.nexacart.config.NexaCartProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        NexaCartProperties properties = new NexaCartProperties();
        properties.getOrders().setShippingFee(new BigDecimal("5.99"));
        properties.getOrders().setFreeShippingThreshold(new BigDecimal("100.00"));
        properties.getOrders().setCurrency("USD");
        pricingService = new PricingService(properties);
    }

    @Test
    void calculatesLineTotalWithTwoDecimalScale() {
        assertThat(pricingService.lineTotal(new BigDecimal("19.955"), 3))
                .isEqualByComparingTo("59.87");
    }

    @Test
    void chargesShippingBelowThreshold() {
        assertThat(pricingService.shippingFor(new BigDecimal("99.99")))
                .isEqualByComparingTo("5.99");
    }

    @Test
    void grantsFreeShippingAtThreshold() {
        assertThat(pricingService.shippingFor(new BigDecimal("100.00")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void emptyCartHasNoShipping() {
        assertThat(pricingService.shippingFor(BigDecimal.ZERO)).isEqualByComparingTo("0.00");
    }

    @Test
    void calculatesTotalWithDiscount() {
        assertThat(pricingService.total(
                new BigDecimal("50.00"),
                new BigDecimal("5.99"),
                new BigDecimal("5.00")
        )).isEqualByComparingTo("50.99");
    }

    @Test
    void totalNeverGoesBelowZero() {
        assertThat(pricingService.total(
                new BigDecimal("10.00"),
                new BigDecimal("0.00"),
                new BigDecimal("25.00")
        )).isEqualByComparingTo("0.00");
    }
}
