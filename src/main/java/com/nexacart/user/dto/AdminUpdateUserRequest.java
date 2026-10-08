package com.nexacart.user.dto;

import com.nexacart.user.entity.Role;
import jakarta.validation.constraints.NotNull;

public record AdminUpdateUserRequest(
        @NotNull Role role,
        @NotNull Boolean enabled
) {
}
