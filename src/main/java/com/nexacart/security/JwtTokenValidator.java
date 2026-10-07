package com.nexacart.security;

import com.nexacart.auth.repository.RevokedTokenRepository;
import com.nexacart.user.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final RevokedTokenRepository revokedTokenRepository;
    private final UserRepository userRepository;

    public JwtTokenValidator(RevokedTokenRepository revokedTokenRepository, UserRepository userRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String jti = token.getId();
        if (jti == null || jti.isBlank()) {
            return failure("Token identifier is missing");
        }
        if (revokedTokenRepository.existsByJti(jti)) {
            return failure("Token has been revoked");
        }
        UUID userId;
        try {
            userId = UUID.fromString(token.getSubject());
        } catch (IllegalArgumentException ex) {
            return failure("Token subject is invalid");
        }
        boolean enabled = userRepository.findById(userId)
                .map(user -> user.isEnabled())
                .orElse(false);
        if (!enabled) {
            return failure("User account is not active");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
