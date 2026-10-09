package com.nexacart.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        Boolean active
) {
}
