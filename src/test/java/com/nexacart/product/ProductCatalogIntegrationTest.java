package com.nexacart.product;

import com.nexacart.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductCatalogIntegrationTest extends IntegrationTestBase {

    @Test
    void adminCanCreateCategoryAndProduct() throws Exception {
        String admin = createAdmin("catalog-admin@example.com");
        UUID categoryId = createCategory(admin, "Audio");
        UUID productId = createProduct(admin, categoryId, "AUD-001", "Studio Headphones", "199.99", 12);

        getJson("/api/v1/products/" + productId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("AUD-001"))
                .andExpect(jsonPath("$.stockQuantity").value(12))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.category.id").value(categoryId.toString()));
    }

    @Test
    void customerCannotManageCatalog() throws Exception {
        String customer = registerCustomer("catalog-customer@example.com");
        postJson("/api/v1/admin/categories", customer, Map.of("name", "Forbidden"))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateSkusAreRejectedCaseInsensitively() throws Exception {
        String admin = createAdmin("sku-admin@example.com");
        UUID categoryId = createCategory(admin, "Parts");
        createProduct(admin, categoryId, "part-1", "Part One", "10.00", 5);

        postJson("/api/v1/admin/products", admin, Map.of(
                        "categoryId", categoryId.toString(),
                        "name", "Part Duplicate",
                        "sku", "PART-1",
                        "price", "12.00",
                        "initialStock", 2
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_EXISTS"));
    }

    @Test
    void searchFiltersByCategoryPriceAvailabilityAndSorts() throws Exception {
        String admin = createAdmin("search-admin@example.com");
        UUID audio = createCategory(admin, "Audio");
        UUID books = createCategory(admin, "Books");
        createProduct(admin, audio, "AUD-100", "Budget Speaker", "25.00", 4);
        createProduct(admin, audio, "AUD-200", "Premium Speaker", "120.00", 0);
        createProduct(admin, books, "BOOK-100", "Java Book", "50.00", 20);

        getJson("/api/v1/products?categoryId=" + audio
                + "&minPrice=10&maxPrice=100&available=true&sort=price,asc", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("AUD-100"));

        getJson("/api/v1/products?search=speaker&sort=price,asc", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].price").value(25.00))
                .andExpect(jsonPath("$.content[1].price").value(120.00));

        getJson("/api/v1/products?available=false", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("AUD-200"));
    }

    @Test
    void paginationMetadataIsReturned() throws Exception {
        String admin = createAdmin("page-admin@example.com");
        UUID category = createCategory(admin, "Paged");
        createProduct(admin, category, "PAGE-1", "Page One", "10.00", 1);
        createProduct(admin, category, "PAGE-2", "Page Two", "20.00", 1);
        createProduct(admin, category, "PAGE-3", "Page Three", "30.00", 1);

        getJson("/api/v1/products?page=0&size=2&sort=price,asc", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].sku").value("PAGE-1"));
    }

    @Test
    void inactiveProductAndCategoryAreHiddenPublicly() throws Exception {
        String admin = createAdmin("visibility-admin@example.com");
        UUID category = createCategory(admin, "Seasonal");
        UUID product = createProduct(admin, category, "SEASON-1", "Seasonal Item", "15.00", 10);

        deleteJson("/api/v1/admin/products/" + product, admin).andExpect(status().isNoContent());
        getJson("/api/v1/products/" + product, null).andExpect(status().isNotFound());
        getJson("/api/v1/products?search=Seasonal", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        UUID secondProduct = createProduct(admin, category, "SEASON-2", "Another Item", "18.00", 10);
        deleteJson("/api/v1/admin/categories/" + category, admin).andExpect(status().isNoContent());
        getJson("/api/v1/products/" + secondProduct, null).andExpect(status().isNotFound());
        getJson("/api/v1/products?categoryId=" + category, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        getJson("/api/v1/admin/products/" + secondProduct, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void invalidSearchParametersAreRejected() throws Exception {
        getJson("/api/v1/products?sort=stockQuantity,asc", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));

        getJson("/api/v1/products?minPrice=50&maxPrice=10", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRICE_RANGE"));
    }

    @Test
    void invalidProductPayloadIsRejected() throws Exception {
        String admin = createAdmin("invalid-product-admin@example.com");
        UUID category = createCategory(admin, "Invalid");
        postJson("/api/v1/admin/products", admin, Map.of(
                        "categoryId", category.toString(),
                        "name", "Bad Product",
                        "sku", "BAD-1",
                        "price", "-1.00",
                        "initialStock", -1
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
