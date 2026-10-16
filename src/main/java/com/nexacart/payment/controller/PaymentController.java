package com.nexacart.payment.controller;

import com.nexacart.payment.dto.PaymentResponse;
import com.nexacart.payment.dto.SimulatePaymentRequest;
import com.nexacart.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/orders/{orderId}/payments")
    @ApiResponse(responseCode = "201", description = "Payment initiated or existing pending payment returned")
    public ResponseEntity<PaymentResponse> initiate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId
    ) {
        PaymentResponse payment = paymentService.initiate(userId(jwt), orderId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/payments/" + payment.id()))
                .body(payment);
    }

    @GetMapping("/orders/{orderId}/payments")
    public List<PaymentResponse> history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId
    ) {
        return paymentService.listForOrder(userId(jwt), orderId, false);
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return paymentService.getPayment(userId(jwt), paymentId, false);
    }

    @PostMapping("/payments/{paymentId}/simulate")
    public PaymentResponse simulate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @Valid @RequestBody SimulatePaymentRequest request
    ) {
        return paymentService.simulateCompletion(userId(jwt), paymentId, request);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
