package com.nexacart.config;

import com.nexacart.address.entity.Address;
import com.nexacart.address.repository.AddressRepository;
import com.nexacart.category.entity.Category;
import com.nexacart.category.repository.CategoryRepository;
import com.nexacart.common.util.SlugUtil;
import com.nexacart.product.dto.CreateProductRequest;
import com.nexacart.product.repository.ProductRepository;
import com.nexacart.product.service.ProductService;
import com.nexacart.user.entity.Role;
import com.nexacart.user.entity.User;
import com.nexacart.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "nexacart.seed", name = "enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private final NexaCartProperties properties;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final ProductService productService;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(
            NexaCartProperties properties,
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            AddressRepository addressRepository,
            ProductService productService,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.productService = productService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User admin = seedAdmin();
        User customer = seedCustomer(
                "customer1@nexacart.local",
                "Maya",
                "Hassan",
                properties.getSeed().getCustomerPassword()
        );
        seedCustomer(
                "customer2@nexacart.local",
                "Omar",
                "Khaled",
                properties.getSeed().getCustomerPassword()
        );
        seedAddress(customer);
        seedCatalog(admin == null ? null : admin.getId());
        log.info("Development seed data is ready");
    }

    private User seedAdmin() {
        String password = properties.getSeed().getAdminPassword();
        if (password == null || password.isBlank()) {
            log.warn("Skipping development administrator seed because NEXACART_ADMIN_PASSWORD is not set");
            return null;
        }
        return userRepository.findByEmailIgnoreCase(properties.getSeed().getAdminEmail())
                .orElseGet(() -> createUser(
                        properties.getSeed().getAdminEmail(),
                        "Nexa",
                        "Admin",
                        password,
                        Role.ADMIN
                ));
    }

    private User seedCustomer(String email, String firstName, String lastName, String password) {
        if (password == null || password.isBlank()) {
            log.warn("Skipping development customer seed because a seed customer password is not set");
            return null;
        }
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> createUser(email, firstName, lastName, password, Role.CUSTOMER));
    }

    private User createUser(String email, String firstName, String lastName, String password, Role role) {
        User user = new User();
        user.setEmail(email.trim().toLowerCase());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private void seedAddress(User customer) {
        if (customer == null || addressRepository.countByUserId(customer.getId()) > 0) {
            return;
        }
        Address address = new Address();
        address.setUser(customer);
        address.setRecipientName(customer.getFullName());
        address.setLine1("42 Demo Street");
        address.setLine2("Apartment 7");
        address.setCity("Cairo");
        address.setState("Cairo");
        address.setPostalCode("11511");
        address.setCountry("Egypt");
        address.setPhone("+20 100 000 0000");
        address.setDefaultAddress(true);
        addressRepository.save(address);
    }

    private void seedCatalog(UUID actorId) {
        Category electronics = category("Electronics", "Phones, audio, and accessories");
        Category home = category("Home & Kitchen", "Useful products for everyday living");
        Category books = category("Books", "Books and learning materials");
        Category fitness = category("Fitness", "Training and wellness equipment");

        List<SeedProduct> products = List.of(
                new SeedProduct("NX-EL-001", "Aurora Noise-Cancelling Headphones", electronics,
                        "Wireless over-ear headphones with active noise cancellation.", "149.99", 35, 8),
                new SeedProduct("NX-EL-002", "Pulse Wireless Earbuds", electronics,
                        "Compact Bluetooth earbuds with charging case.", "59.99", 80, 10),
                new SeedProduct("NX-EL-003", "Nimbus Smart Speaker", electronics,
                        "Voice-enabled speaker with room-filling sound.", "89.50", 20, 5),
                new SeedProduct("NX-EL-004", "Volt 65W USB-C Charger", electronics,
                        "Fast multi-port charger for laptops and phones.", "39.00", 120, 15),
                new SeedProduct("NX-HK-001", "Brew Master Coffee Maker", home,
                        "Programmable drip coffee maker with thermal carafe.", "79.99", 18, 4),
                new SeedProduct("NX-HK-002", "Chef's Ceramic Knife Set", home,
                        "Three-piece ceramic knife set with bamboo block.", "54.25", 12, 3),
                new SeedProduct("NX-HK-003", "Glow Desk Lamp", home,
                        "Adjustable LED desk lamp with three color modes.", "34.90", 45, 6),
                new SeedProduct("NX-BK-001", "Clean Architecture in Practice", books,
                        "A practical guide to maintainable software design.", "42.00", 25, 5),
                new SeedProduct("NX-BK-002", "The Pragmatic Java Developer", books,
                        "Patterns and techniques for professional Java engineers.", "38.50", 30, 5),
                new SeedProduct("NX-FT-001", "Grip Pro Yoga Mat", fitness,
                        "Non-slip six-millimeter yoga mat.", "29.99", 40, 8),
                new SeedProduct("NX-FT-002", "Momentum Adjustable Dumbbells", fitness,
                        "Space-saving adjustable dumbbell pair.", "199.00", 6, 2),
                new SeedProduct("NX-FT-003", "Flow Resistance Band Set", fitness,
                        "Five resistance levels with carry bag.", "24.50", 0, 5)
        );

        for (SeedProduct seed : products) {
            if (productRepository.existsBySkuIgnoreCase(seed.sku())) {
                continue;
            }
            productService.create(actorId, new CreateProductRequest(
                    seed.category().getId(),
                    seed.name(),
                    seed.sku(),
                    seed.description(),
                    new BigDecimal(seed.price()),
                    seed.stock(),
                    seed.lowStockThreshold()
            ));
        }
    }

    private Category category(String name, String description) {
        return categoryRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName(name);
                    category.setSlug(SlugUtil.slugify(name));
                    category.setDescription(description);
                    category.setActive(true);
                    return categoryRepository.save(category);
                });
    }

    private record SeedProduct(
            String sku,
            String name,
            Category category,
            String description,
            String price,
            int stock,
            int lowStockThreshold
    ) {
    }
}
