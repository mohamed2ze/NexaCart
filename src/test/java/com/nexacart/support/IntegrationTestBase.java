package com.nexacart.support;

import com.jayway.jsonpath.JsonPath;
import com.nexacart.user.entity.Role;
import com.nexacart.user.entity.User;
import com.nexacart.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    public static final String DEFAULT_PASSWORD = "Password123!";

    protected static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRESQL.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE reviews, payments, order_items, orders, cart_items, carts,
                inventory_transactions, inventory, products, categories, addresses, revoked_tokens, users
                RESTART IDENTITY CASCADE
                """);
    }

    protected ResultActions getJson(String path, String token) throws Exception {
        return mockMvc.perform(withAuth(get(path), token));
    }

    protected ResultActions postJson(String path, String token, Object body) throws Exception {
        var request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
        return mockMvc.perform(withAuth(request, token));
    }

    protected ResultActions postOrder(String token, String idempotencyKey, Object body) throws Exception {
        var request = post("/api/v1/orders")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
        return mockMvc.perform(withAuth(request, token));
    }

    protected ResultActions patchJson(String path, String token, Object body) throws Exception {
        var request = patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
        return mockMvc.perform(withAuth(request, token));
    }

    protected ResultActions putJson(String path, String token, Object body) throws Exception {
        var request = put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
        return mockMvc.perform(withAuth(request, token));
    }

    protected ResultActions deleteJson(String path, String token) throws Exception {
        return mockMvc.perform(withAuth(delete(path), token));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withAuth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token
    ) {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return request;
    }

    protected String registerCustomer(String email) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", DEFAULT_PASSWORD);
        body.put("firstName", "Test");
        body.put("lastName", "Customer");
        postJson("/api/v1/auth/register", null, body).andExpect(status().isCreated());
        return login(email, DEFAULT_PASSWORD);
    }

    protected String createAdmin(String email) throws Exception {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setFirstName("Admin");
        user.setLastName("Tester");
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        userRepository.saveAndFlush(user);
        return login(email, DEFAULT_PASSWORD);
    }

    protected String login(String email, String password) throws Exception {
        Map<String, Object> body = Map.of("email", email, "password", password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    protected UUID userId(String token) throws Exception {
        MvcResult result = getJson("/api/v1/users/me", token)
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    protected UUID createCategory(String adminToken, String name) throws Exception {
        MvcResult result = postJson("/api/v1/admin/categories", adminToken, Map.of(
                        "name", name,
                        "description", name + " description"
                ))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(id(result));
    }

    protected UUID createProduct(
            String adminToken,
            UUID categoryId,
            String sku,
            String name,
            String price,
            int initialStock
    ) throws Exception {
        MvcResult result = postJson("/api/v1/admin/products", adminToken, Map.of(
                        "categoryId", categoryId.toString(),
                        "name", name,
                        "sku", sku,
                        "description", name + " description",
                        "price", price,
                        "initialStock", initialStock,
                        "lowStockThreshold", 2
                ))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(id(result));
    }

    protected UUID createAddress(String token) throws Exception {
        MvcResult result = postJson("/api/v1/addresses", token, Map.of(
                        "recipientName", "Test Recipient",
                        "line1", "10 Test Street",
                        "line2", "Suite 4",
                        "city", "Cairo",
                        "state", "Cairo",
                        "postalCode", "11511",
                        "country", "Egypt",
                        "phone", "+20 100 123 4567",
                        "defaultAddress", true
                ))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(id(result));
    }

    protected void addToCart(String token, UUID productId, int quantity) throws Exception {
        postJson("/api/v1/cart/items", token, Map.of(
                        "productId", productId.toString(),
                        "quantity", quantity
                ))
                .andExpect(status().isOk());
    }

    protected MvcResult placeOrder(String token, String idempotencyKey, UUID addressId) throws Exception {
        return postOrder(token, idempotencyKey, Map.of("addressId", addressId.toString()))
                .andExpect(status().isCreated())
                .andReturn();
    }

    protected String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected java.math.BigDecimal decimal(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return new java.math.BigDecimal(value.toString());
    }

    protected long stock(UUID productId) {
        return jdbcTemplate.queryForObject(
                "select quantity from inventory where product_id = ?",
                Long.class,
                productId
        );
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected String bearer(String token) {
        return token == null ? null : "Bearer " + token;
    }
}
