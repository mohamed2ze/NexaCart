package com.nexacart.product.dto;

import com.nexacart.category.entity.Category;

import java.util.UUID;

public record CategorySummary(UUID id, String name, String slug) {

    public static CategorySummary from(Category category) {
        return new CategorySummary(category.getId(), category.getName(), category.getSlug());
    }
}
