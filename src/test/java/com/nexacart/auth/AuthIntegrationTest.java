package com.nexacart.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nexacart.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void registrationAndLoginReturnSafeUserData() throws Exception {
        Map<String, Object> body = Map.of(
                "email", "New.Customer@Example.com",
                "password", DEFAULT_PASSWORD,
                "firstName", "New",
                "lastName", "Customer"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.customer@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        String token = login("new.customer@example.com", DEFAULT_PASSWORD);
        getJson("/api/v1/users/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new.customer@example.com"));

        String storedHash = jdbcTemplate.queryForObject(
                "select password_hash from users where email = 'new.customer@example.com'",
                String.class
        );
        assertThat(storedHash)
                .startsWith("$2")
                .isNotEqualTo(DEFAULT_PASSWORD);
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        registerCustomer("duplicate@example.com");
        Map<String, Object> body = Map.of(
                "email", "DUPLICATE@example.com",
                "password", DEFAULT_PASSWORD,
                "firstName", "Other",
                "lastName", "User"
        );
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void publicRegistrationCannotAssignAdminRole() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "role.attacker@example.com");
        body.put("password", DEFAULT_PASSWORD);
        body.put("firstName", "Role");
        body.put("lastName", "Attacker");
        body.put("role", "ADMIN");
        body.put("enabled", true);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        String role = jdbcTemplate.queryForObject(
                "select role from users where email = 'role.attacker@example.com'",
                String.class
        );
        assertThat(role).isEqualTo("CUSTOMER");
    }

    @Test
    void invalidCredentialsUseGenericUnauthorizedResponse() throws Exception {
        registerCustomer("login@example.com");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "login@example.com", "password", "WrongPass123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "missing@example.com", "password", "WrongPass123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void logoutRevokesTokenServerSide() throws Exception {
        String token = registerCustomer("logout@example.com");
        getJson("/api/v1/users/me", token).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        getJson("/api/v1/users/me", token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void disablingAccountInvalidatesExistingTokens() throws Exception {
        String adminToken = createAdmin("admin-disable@example.com");
        String customerToken = registerCustomer("disabled@example.com");
        String customerId = userId(customerToken).toString();

        patchJson("/api/v1/admin/users/" + customerId, adminToken, Map.of(
                        "role", "CUSTOMER",
                        "enabled", false
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        getJson("/api/v1/users/me", customerToken).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "disabled@example.com", "password", DEFAULT_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDisableOwnAccount() throws Exception {
        String adminToken = createAdmin("self-admin@example.com");
        String adminId = userId(adminToken).toString();
        patchJson("/api/v1/admin/users/" + adminId, adminToken, Map.of(
                        "role", "ADMIN",
                        "enabled", false
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_ADMIN_CHANGE_NOT_ALLOWED"));
    }

    @Test
    void customerCannotAccessAdminEndpoints() throws Exception {
        String customerToken = registerCustomer("not-admin@example.com");
        getJson("/api/v1/admin/users", customerToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void roleChangesTakeEffectWithoutWaitingForTokenExpiry() throws Exception {
        String firstAdmin = createAdmin("role-admin-one@example.com");
        String secondAdmin = createAdmin("role-admin-two@example.com");
        String secondAdminId = userId(secondAdmin).toString();

        getJson("/api/v1/admin/users", secondAdmin).andExpect(status().isOk());

        patchJson("/api/v1/admin/users/" + secondAdminId, firstAdmin, Map.of(
                        "role", "CUSTOMER",
                        "enabled", true
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        getJson("/api/v1/admin/users", secondAdmin)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void protectedEndpointRequiresValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredSignedTokenIsRejected() throws Exception {
        String customer = registerCustomer("expired-token@example.com");
        UUID customerId = userId(customer);
        SecretKey key = new SecretKeySpec(
                Base64.getDecoder().decode("bmV4YWNhcnQtdGVzdC1zZWNyZXQtMDEyMzQ1Njc4OWFiY2RlZg=="),
                "HmacSHA256"
        );
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("nexacart-test")
                .subject(customerId.toString())
                .issuedAt(now.minusSeconds(600))
                .expiresAt(now.minusSeconds(300))
                .id(UUID.randomUUID().toString())
                .build();
        String expiredToken = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();

        getJson("/api/v1/users/me", expiredToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void registrationValidationReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "not-an-email", "password", "short", "firstName", "", "lastName", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }
}
