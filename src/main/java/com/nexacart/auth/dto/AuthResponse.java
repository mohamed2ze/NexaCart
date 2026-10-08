package com.nexacart.auth.dto;

import com.nexacart.user.dto.UserResponse;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Instant expiresAt,
        UserResponse user
) {
}
