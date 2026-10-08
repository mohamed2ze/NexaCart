package com.nexacart.auth.service;

import com.nexacart.auth.dto.AuthResponse;
import com.nexacart.auth.dto.LoginRequest;
import com.nexacart.auth.dto.RegisterRequest;
import com.nexacart.auth.repository.RevokedTokenRepository;
import com.nexacart.common.exception.ApiException;
import com.nexacart.security.JwtService;
import com.nexacart.user.dto.UserResponse;
import com.nexacart.user.entity.Role;
import com.nexacart.user.entity.User;
import com.nexacart.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            RevokedTokenRepository revokedTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-never-used-123");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setRole(Role.CUSTOMER);
        user.setEnabled(true);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        String hash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches || !user.isEnabled()) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }
        JwtService.IssuedToken issuedToken = jwtService.issue(user);
        return new AuthResponse(
                issuedToken.token(),
                "Bearer",
                issuedToken.expiresInSeconds(),
                issuedToken.expiresAt(),
                UserResponse.from(user)
        );
    }

    @Transactional
    public void logout(Jwt jwt) {
        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            throw ApiException.badRequest("INVALID_TOKEN_ID", "Token does not contain a valid identifier");
        }
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Token subject is invalid");
        }
        Instant expiresAt = jwt.getExpiresAt() == null ? Instant.now() : jwt.getExpiresAt();
        revokedTokenRepository.insertIfAbsent(UUID.randomUUID(), jti, userId, expiresAt, Instant.now());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
