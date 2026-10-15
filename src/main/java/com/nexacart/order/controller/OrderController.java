package com.nexacart.order.controller;

import com.nexacart.common.response.PageResponse;
import com.nexacart.order.dto.CreateOrderRequest;
import com.nexacart.order.dto.OrderResponse;
import com.nexacart.order.dto.OrderSummaryResponse;
import com.nexacart.order.entity.OrderStatus;
import com.nexacart.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Create an order from the current cart")
    @ApiResponse(responseCode = "201", description = "Order created or idempotent original returned")
    @ApiResponse(responseCode = "409", description = "Insufficient stock, unavailable product, or duplicate conflict")
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse created = orderService.createOrder(userId(jwt), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/orders/" + created.id()))
                .body(created);
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return orderService.listOrders(userId(jwt), status, page, size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return orderService.getOrder(userId(jwt), orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return orderService.cancelByCustomer(userId(jwt), orderId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
