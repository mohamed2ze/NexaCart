package com.nexacart.inventory.controller;

import com.nexacart.common.response.PageResponse;
import com.nexacart.inventory.dto.AdjustStockRequest;
import com.nexacart.inventory.dto.InventoryResponse;
import com.nexacart.inventory.dto.InventoryTransactionResponse;
import com.nexacart.inventory.service.InventoryService;
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
@RequestMapping("/api/v1/admin/inventory")
@Tag(name = "Admin Inventory")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/low-stock")
    public List<InventoryResponse> lowStock() {
        return inventoryService.lowStock();
    }

    @GetMapping("/{productId}")
    public InventoryResponse get(@PathVariable UUID productId) {
        return inventoryService.getInventory(productId);
    }

    @PatchMapping("/{productId}/stock")
    public InventoryResponse adjustStock(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody AdjustStockRequest request
    ) {
        return inventoryService.adjustStock(UUID.fromString(jwt.getSubject()), productId, request);
    }

    @GetMapping("/{productId}/transactions")
    public PageResponse<InventoryTransactionResponse> history(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return inventoryService.history(productId, page, size);
    }
}
