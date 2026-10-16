package com.nexacart.payment.dto;

import com.nexacart.payment.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        String provider,
        String providerReference,
        String status,
        BigDecimal amount,
        String currency,
        String failureReason,
        String refundReference,
        Instant paidAt,
        Instant refundedAt,
        Instant createdAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getProvider(),
                payment.getProviderReference(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getFailureReason(),
                payment.getRefundReference(),
                payment.getPaidAt(),
                payment.getRefundedAt(),
                payment.getCreatedAt()
        );
    }
}
