package com.nexacart.category.controller;

import com.nexacart.category.dto.CategoryResponse;
import com.nexacart.category.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.listPublic();
    }

    @GetMapping("/{categoryId}")
    public CategoryResponse get(@PathVariable UUID categoryId) {
        return categoryService.getPublic(categoryId);
    }
}
