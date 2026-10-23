package com.nexacart.inventory;

import com.nexacart.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryIntegrationTest extends IntegrationTestBase {

    @Test
    void adminAdjustsStockAndHistoryIsRecorded() throws Exception {
        String admin = createAdmin("inventory-admin@example.com");
        UUID category = createCategory(admin, "Inventory Category");
        UUID product = createProduct(admin, category, "INV-1", "Inventory Product", "19.99", 10);

        getJson("/api/v1/admin/inventory/" + product, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.lowStockThreshold").value(2))
                .andExpect(jsonPath("$.lowStock").value(false));

        patchJson("/api/v1/admin/inventory/" + product + "/stock", admin, Map.of(
                        "delta", -3,
                        "reason", "Damaged units removed"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.lowStock").value(false));

        patchJson("/api/v1/admin/inventory/" + product + "/stock", admin, Map.of(
                        "quantity", 1,
                        "lowStockThreshold", 4,
                        "reason", "Physical count correction"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.lowStockThreshold").value(4))
                .andExpect(jsonPath("$.lowStock").value(true));

        getJson("/api/v1/admin/inventory/low-stock", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(product.toString()));

        getJson("/api/v1/admin/inventory/" + product + "/transactions", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].reason").value("Physical count correction"))
                .andExpect(jsonPath("$.content[0].performedBy").value("inventory-admin@example.com"));
    }

    @Test
    void invalidStockAdjustmentsAreRejected() throws Exception {
        String admin = createAdmin("invalid-inventory-admin@example.com");
        UUID category = createCategory(admin, "Invalid Inventory");
        UUID product = createProduct(admin, category, "INV-2", "Inventory Rules", "15.00", 3);

        patchJson("/api/v1/admin/inventory/" + product + "/stock", admin, Map.of(
                        "delta", -4,
                        "reason", "Too large"
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEGATIVE_STOCK"));

        patchJson("/api/v1/admin/inventory/" + product + "/stock", admin, Map.of(
                        "delta", 0,
                        "reason", "Zero change"
                ))
                .andExpect(status().isBadRequest());

        patchJson("/api/v1/admin/inventory/" + product + "/stock", admin, Map.of(
                        "delta", 1,
                        "quantity", 5,
                        "reason", "Ambiguous"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STOCK_ADJUSTMENT"));

        patchJson("/api/v1/admin/inventory/" + product + "/stock", admin, Map.of(
                        "quantity", 3,
                        "reason", "No change at all"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_STOCK_CHANGE"));
    }

    @Test
    void inventoryEndpointsRequireAdmin() throws Exception {
        String admin = createAdmin("inventory-auth-admin@example.com");
        UUID category = createCategory(admin, "Inventory Auth");
        UUID product = createProduct(admin, category, "INV-3", "Protected Inventory", "15.00", 3);
        String customer = registerCustomer("inventory-customer@example.com");

        getJson("/api/v1/admin/inventory/" + product, customer)
                .andExpect(status().isForbidden());
        patchJson("/api/v1/admin/inventory/" + product + "/stock", customer, Map.of(
                        "delta", 5,
                        "reason", "Unauthorized"
                ))
                .andExpect(status().isForbidden());
    }
}
