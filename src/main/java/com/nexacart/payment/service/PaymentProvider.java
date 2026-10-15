package com.nexacart.payment.service;

import com.nexacart.order.entity.Order;
import com.nexacart.payment.entity.Payment;

public interface PaymentProvider {

    String name();

    Initiation initiate(Order order);

    RefundResult refund(Payment payment);

    record Initiation(String providerReference) {
    }

    record RefundResult(String refundReference) {
    }
}
