package com.nexacart.order.controller;

import com.nexacart.common.response.PageResponse;
import com.nexacart.order.dto.AdminOrderSummaryResponse;
import com.nexacart.order.dto.OrderResponse;
import com.nexacart.order.dto.UpdateOrderStatusRequest;
import com.nexacart.order.entity.OrderStatus;
import com.nexacart.order.service.OrderService;
import com.nexacart.payment.dto.PaymentResponse;
import com.nexacart.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin Orders")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminOrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public AdminOrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public PageResponse<AdminOrderSummaryResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return orderService.adminList(status, userId, page, size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        return orderService.adminGet(orderId);
    }

    @PatchMapping("/{orderId}/status")
    public OrderResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateStatusByAdmin(UUID.fromString(jwt.getSubject()), orderId, request.status());
    }

    @GetMapping("/{orderId}/payments")
    public List<PaymentResponse> payments(@PathVariable UUID orderId) {
        return paymentService.listForOrder(null, orderId, true);
    }
}
