package com.nexacart.payment.service;

import com.nexacart.order.entity.Order;
import com.nexacart.payment.entity.Payment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    public static final String PROVIDER_NAME = "SIMULATED";

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public Initiation initiate(Order order) {
        return new Initiation("sim_" + UUID.randomUUID());
    }

    @Override
    public RefundResult refund(Payment payment) {
        return new RefundResult("sim_refund_" + UUID.randomUUID());
    }
}
