package com.nexacart.product.service;

import com.nexacart.category.entity.Category;
import com.nexacart.category.repository.CategoryRepository;
import com.nexacart.common.exception.ApiException;
import com.nexacart.common.response.PageResponse;
import com.nexacart.common.util.SlugUtil;
import com.nexacart.inventory.service.InventoryService;
import com.nexacart.product.dto.CreateProductRequest;
import com.nexacart.product.dto.ProductResponse;
import com.nexacart.product.dto.UpdateProductRequest;
import com.nexacart.product.entity.Product;
import com.nexacart.product.entity.ProductStatus;
import com.nexacart.product.repository.ProductRepository;
import com.nexacart.product.repository.ProductSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "price", "createdAt", "updatedAt", "sku");
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryService inventoryService;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            InventoryService inventoryService
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchPublic(
            String search,
            UUID categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean available,
            int page,
            int size,
            String sort
    ) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw ApiException.badRequest("INVALID_PRICE_RANGE", "minPrice cannot be greater than maxPrice");
        }
        Specification<Product> specification = ProductSpecifications.publiclyVisible();
        specification = addIfPresent(specification, ProductSpecifications.search(search));
        specification = addIfPresent(specification, ProductSpecifications.categoryId(categoryId));
        specification = addIfPresent(specification, ProductSpecifications.minPrice(minPrice));
        specification = addIfPresent(specification, ProductSpecifications.maxPrice(maxPrice));
        specification = addIfPresent(specification, ProductSpecifications.available(available));
        Page<Product> products = productRepository.findAll(specification, pageable(page, size, sort));
        return PageResponse.from(products.map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listAdmin(
            String search,
            UUID categoryId,
            ProductStatus status,
            int page,
            int size,
            String sort
    ) {
        Specification<Product> specification = (root, query, cb) -> cb.conjunction();
        specification = addIfPresent(specification, ProductSpecifications.search(search));
        specification = addIfPresent(specification, ProductSpecifications.categoryId(categoryId));
        specification = addIfPresent(specification, ProductSpecifications.status(status));
        Page<Product> products = productRepository.findAll(specification, pageable(page, size, sort));
        return PageResponse.from(products.map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse getPublic(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found"));
        if (!product.isPurchasable()) {
            throw ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found");
        }
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getAdmin(UUID productId) {
        return ProductResponse.from(getRequired(productId));
    }

    @Transactional
    public ProductResponse create(UUID actorId, CreateProductRequest request) {
        Category category = getActiveCategory(request.categoryId());
        String sku = normalizeSku(request.sku());
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict("SKU_EXISTS", "A product with this SKU already exists");
        }
        Product product = new Product();
        product.setCategory(category);
        product.setName(request.name().trim());
        product.setSku(sku);
        product.setSlug(uniqueSlug(request.name()));
        product.setDescription(trimToNull(request.description()));
        product.setPrice(normalizePrice(request.price()));
        product.setStatus(ProductStatus.ACTIVE);
        Product saved = productRepository.saveAndFlush(product);
        int threshold = request.lowStockThreshold() == null ? 5 : request.lowStockThreshold();
        var inventory = inventoryService.initialize(saved, request.initialStock(), threshold, actorId);
        saved.setInventory(inventory);
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(UUID productId, UpdateProductRequest request) {
        Product product = getRequired(productId);
        Category category = getActiveCategory(request.categoryId());
        String sku = normalizeSku(request.sku());
        if (productRepository.existsBySkuIgnoreCaseAndIdNot(sku, productId)) {
            throw ApiException.conflict("SKU_EXISTS", "A product with this SKU already exists");
        }
        if (request.status() == ProductStatus.ACTIVE && !category.isActive()) {
            throw ApiException.badRequest("INACTIVE_CATEGORY", "Active products require an active category");
        }
        product.setCategory(category);
        product.setName(request.name().trim());
        product.setSku(sku);
        product.setDescription(trimToNull(request.description()));
        product.setPrice(normalizePrice(request.price()));
        product.setStatus(request.status());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void deactivate(UUID productId) {
        Product product = getRequired(productId);
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getRequired(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found"));
    }

    private Category getActiveCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category not found"));
        if (!category.isActive()) {
            throw ApiException.badRequest("INACTIVE_CATEGORY", "Products require an active category");
        }
        return category;
    }

    private Pageable pageable(int page, int size, String sort) {
        if (page < 0) {
            throw ApiException.badRequest("INVALID_PAGE", "page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("INVALID_PAGE_SIZE", "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String field = "createdAt";
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            field = parts[0].trim();
            if (!SORTABLE_FIELDS.contains(field)) {
                throw ApiException.badRequest("INVALID_SORT", "Unsupported sort field: " + field);
            }
            if (parts.length == 2) {
                try {
                    direction = Sort.Direction.fromString(parts[1].trim());
                } catch (IllegalArgumentException ex) {
                    throw ApiException.badRequest("INVALID_SORT", "Sort direction must be asc or desc");
                }
            }
        }
        return PageRequest.of(page, size, Sort.by(direction, field));
    }

    private Specification<Product> addIfPresent(Specification<Product> base, Specification<Product> addition) {
        return addition == null ? base : base.and(addition);
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price.signum() < 0) {
            throw ApiException.badRequest("INVALID_PRICE", "Price cannot be negative");
        }
        return price.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String uniqueSlug(String name) {
        String base = SlugUtil.slugify(name);
        String candidate = base;
        while (productRepository.existsBySlug(candidate)) {
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
