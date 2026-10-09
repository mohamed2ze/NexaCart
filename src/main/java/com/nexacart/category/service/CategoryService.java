package com.nexacart.category.service;

import com.nexacart.category.dto.CategoryRequest;
import com.nexacart.category.dto.CategoryResponse;
import com.nexacart.category.entity.Category;
import com.nexacart.category.repository.CategoryRepository;
import com.nexacart.common.exception.ApiException;
import com.nexacart.common.util.SlugUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listPublic() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getPublic(UUID categoryId) {
        Category category = categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category not found"));
        return CategoryResponse.from(category);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getAny(UUID categoryId) {
        return CategoryResponse.from(getRequired(categoryId));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("CATEGORY_NAME_EXISTS", "A category with this name already exists");
        }
        Category category = new Category();
        category.setName(name);
        category.setSlug(uniqueSlug(name));
        category.setDescription(trimToNull(request.description()));
        category.setActive(request.active() == null || request.active());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID categoryId, CategoryRequest request) {
        Category category = getRequired(categoryId);
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, categoryId)) {
            throw ApiException.conflict("CATEGORY_NAME_EXISTS", "A category with this name already exists");
        }
        boolean nameChanged = !category.getName().equalsIgnoreCase(name);
        category.setName(name);
        if (nameChanged) {
            category.setSlug(uniqueSlug(name));
        }
        category.setDescription(trimToNull(request.description()));
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void deactivate(UUID categoryId) {
        Category category = getRequired(categoryId);
        category.setActive(false);
        categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public Category getRequired(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category not found"));
    }

    private String uniqueSlug(String name) {
        String base = SlugUtil.slugify(name);
        String candidate = base;
        while (categoryRepository.existsBySlug(candidate)) {
            candidate = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return candidate;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
