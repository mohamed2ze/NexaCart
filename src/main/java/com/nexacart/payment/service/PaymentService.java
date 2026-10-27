package com.nexacart.payment.service;

import com.nexacart.common.exception.ApiException;
import com.nexacart.config.NexaCartProperties;
import com.nexacart.order.entity.Order;
import com.nexacart.order.entity.OrderStatus;
import com.nexacart.order.repository.OrderRepository;
import com.nexacart.order.service.OrderStateMachine;
import com.nexacart.payment.dto.PaymentResponse;
import com.nexacart.payment.dto.SimulatePaymentRequest;
import com.nexacart.payment.entity.Payment;
import com.nexacart.payment.entity.PaymentStatus;
import com.nexacart.payment.repository.PaymentRepository;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentProvider paymentProvider;
    private final NexaCartProperties properties;
    private final Environment environment;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentProvider paymentProvider,
            NexaCartProperties properties,
            Environment environment
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentProvider = paymentProvider;
        this.properties = properties;
        this.environment = environment;
    }

    @Transactional
    public PaymentResponse initiate(UUID userId, UUID orderId) {
        requireSimulatorEnabled();
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
        if (!order.getUser().getId().equals(userId)) {
            throw ApiException.notFound("ORDER_NOT_FOUND", "Order not found");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw ApiException.conflict(
                    "ORDER_NOT_PAYABLE",
                    "Payments can only be initiated for PENDING orders"
            );
        }
        var existing = paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(
                orderId,
                PaymentStatus.PENDING
        );
        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }
        PaymentProvider.Initiation initiation = paymentProvider.initiate(order);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(paymentProvider.name());
        payment.setProviderReference(initiation.providerReference());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency(order.getCurrency());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse simulateCompletion(
            UUID userId,
            UUID paymentId,
            SimulatePaymentRequest request
    ) {
        requireSimulatorEnabled();
        Payment preview = paymentRepository.findByIdWithOrder(paymentId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "Payment not found"));
        UUID orderId = preview.getOrder().getId();
        // Match cancellation's lock order (order first, then payment) so completion
        // and refunds cannot interleave.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
        if (!order.getUser().getId().equals(userId)) {
            throw ApiException.notFound("PAYMENT_NOT_FOUND", "Payment not found");
        }
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "Payment not found"));
        if (!payment.getOrder().getId().equals(orderId)) {
            throw ApiException.conflict("PAYMENT_ORDER_MISMATCH", "Payment does not belong to the locked order");
        }
        if (!SimulatedPaymentProvider.PROVIDER_NAME.equals(payment.getProvider())) {
            throw ApiException.conflict(
                    "UNSUPPORTED_PROVIDER",
                    "The development simulator can only complete simulated payments"
            );
        }

        if (request.outcome() == SimulatePaymentRequest.Outcome.SUCCESS) {
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                return PaymentResponse.from(payment);
            }
            if (payment.getStatus() != PaymentStatus.PENDING) {
                throw ApiException.conflict(
                        "PAYMENT_ALREADY_FINALIZED",
                        "Payment is already " + payment.getStatus()
                );
            }
            if (order.getStatus() == OrderStatus.CANCELLED) {
                throw ApiException.conflict("ORDER_CANCELLED", "Cannot complete payment for a cancelled order");
            }
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setPaidAt(Instant.now());
            payment.setFailureReason(null);
            paymentRepository.save(payment);

            if (order.getStatus() == OrderStatus.PENDING) {
                OrderStateMachine.requireTransition(order.getStatus(), OrderStatus.CONFIRMED);
                order.setStatus(OrderStatus.CONFIRMED);
                orderRepository.save(order);
            }
            return PaymentResponse.from(payment);
        }

        if (payment.getStatus() == PaymentStatus.FAILED) {
            return PaymentResponse.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.conflict(
                    "PAYMENT_ALREADY_FINALIZED",
                    "Payment is already " + payment.getStatus()
            );
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.conflict("ORDER_CANCELLED", "Order has already been cancelled");
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Simulated payment failure");
        paymentRepository.save(payment);
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForOrder(UUID userId, UUID orderId, boolean admin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
        if (!admin && !order.getUser().getId().equals(userId)) {
            throw ApiException.notFound("ORDER_NOT_FOUND", "Order not found");
        }
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID userId, UUID paymentId, boolean admin) {
        Payment payment = paymentRepository.findByIdWithOrder(paymentId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "Payment not found"));
        if (!admin && !payment.getOrder().getUser().getId().equals(userId)) {
            throw ApiException.notFound("PAYMENT_NOT_FOUND", "Payment not found");
        }
        return PaymentResponse.from(payment);
    }

    private void requireSimulatorEnabled() {
        boolean productionProfile = environment.acceptsProfiles(Profiles.of("prod | production"));
        if (productionProfile || !properties.getPayments().isSimulatorEnabled()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_PROVIDER_UNAVAILABLE",
                    "Payments are disabled. Configure a real payment provider or enable the development simulator."
            );
        }
    }
}
