package com.nexacart.order.service;

import com.nexacart.address.entity.Address;
import com.nexacart.address.repository.AddressRepository;
import com.nexacart.cart.entity.Cart;
import com.nexacart.cart.entity.CartItem;
import com.nexacart.cart.repository.CartItemRepository;
import com.nexacart.cart.repository.CartRepository;
import com.nexacart.common.exception.ApiException;
import com.nexacart.common.pricing.PricingService;
import com.nexacart.common.response.PageResponse;
import com.nexacart.inventory.entity.Inventory;
import com.nexacart.inventory.entity.InventoryTransaction;
import com.nexacart.inventory.entity.InventoryTransactionType;
import com.nexacart.inventory.repository.InventoryRepository;
import com.nexacart.inventory.repository.InventoryTransactionRepository;
import com.nexacart.order.dto.AdminOrderSummaryResponse;
import com.nexacart.order.dto.CreateOrderRequest;
import com.nexacart.order.dto.OrderResponse;
import com.nexacart.order.dto.OrderSummaryResponse;
import com.nexacart.order.entity.Order;
import com.nexacart.order.entity.OrderItem;
import com.nexacart.order.entity.OrderStatus;
import com.nexacart.order.repository.OrderRepository;
import com.nexacart.order.repository.OrderSpecifications;
import com.nexacart.payment.entity.Payment;
import com.nexacart.payment.entity.PaymentStatus;
import com.nexacart.payment.repository.PaymentRepository;
import com.nexacart.payment.service.PaymentProvider;
import com.nexacart.product.entity.Product;
import com.nexacart.user.entity.User;
import com.nexacart.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<OrderStatus> CUSTOMER_CANCELLABLE =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED);
    private static final Set<OrderStatus> ADMIN_CANCELLABLE =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING);

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PricingService pricingService;
    private final TransactionTemplate transactionTemplate;

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            AddressRepository addressRepository,
            InventoryRepository inventoryRepository,
            InventoryTransactionRepository inventoryTransactionRepository,
            UserRepository userRepository,
            PaymentRepository paymentRepository,
            PaymentProvider paymentProvider,
            PricingService pricingService,
            PlatformTransactionManager transactionManager
    ) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.addressRepository = addressRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
        this.pricingService = pricingService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public OrderResponse createOrder(UUID userId, String idempotencyKey, CreateOrderRequest request) {
        String key = idempotencyKey.trim();
        var existing = orderRepository.findByUserIdAndIdempotencyKey(userId, key);
        if (existing.isPresent()) {
            return OrderResponse.from(existing.get());
        }
        try {
            return transactionTemplate.execute(status -> createOrderInTransaction(userId, key, request));
        } catch (DataIntegrityViolationException ex) {
            var concurrentOrder = orderRepository.findByUserIdAndIdempotencyKey(userId, key);
            if (concurrentOrder.isPresent()) {
                return OrderResponse.from(concurrentOrder.get());
            }
            throw ex;
        }
    }

    private OrderResponse createOrderInTransaction(UUID userId, String key, CreateOrderRequest request) {
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> ApiException.unprocessable("CART_EMPTY", "The cart is empty"));

        var existing = orderRepository.findByUserIdAndIdempotencyKey(userId, key);
        if (existing.isPresent()) {
            return OrderResponse.from(existing.get());
        }

        Address address = addressRepository.findByIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> ApiException.notFound("ADDRESS_NOT_FOUND", "Address not found"));

        List<CartItem> cartItems = cartItemRepository.findForCheckout(cart.getId());
        if (cartItems.isEmpty()) {
            throw ApiException.unprocessable("CART_EMPTY", "The cart is empty");
        }

        List<UUID> productIds = cartItems.stream()
                .map(item -> item.getProduct().getId())
                .sorted()
                .toList();
        List<Inventory> inventories = inventoryRepository.findAllByProductIdInForUpdate(productIds);
        Map<UUID, Inventory> inventoryByProduct = new HashMap<>();
        for (Inventory inventory : inventories) {
            inventoryByProduct.put(inventory.getProduct().getId(), inventory);
        }
        if (inventoryByProduct.size() != productIds.size()) {
            throw ApiException.conflict("INVENTORY_NOT_FOUND", "Inventory is missing for one or more products");
        }

        for (CartItem cartItem : cartItems) {
            Inventory inventory = inventoryByProduct.get(cartItem.getProduct().getId());
            validateLineForCheckout(cartItem, inventory);
        }

        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cartItems) {
            Inventory inventory = inventoryByProduct.get(cartItem.getProduct().getId());
            Product product = inventory.getProduct();
            BigDecimal lineTotal = pricingService.lineTotal(product.getPrice(), cartItem.getQuantity());
            subtotal = subtotal.add(lineTotal);
            quantities.put(product.getId(), cartItem.getQuantity());
        }
        subtotal = pricingService.normalize(subtotal);
        BigDecimal shippingCost = pricingService.shippingFor(subtotal);
        BigDecimal discount = pricingService.normalize(BigDecimal.ZERO);
        BigDecimal total = pricingService.total(subtotal, shippingCost, discount);

        User user = userRepository.getReferenceById(userId);
        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setSubtotal(subtotal);
        order.setShippingCost(shippingCost);
        order.setDiscountAmount(discount);
        order.setTotalAmount(total);
        order.setCurrency(pricingService.currency());
        order.setIdempotencyKey(key);
        order.setRecipientName(address.getRecipientName());
        order.setShippingLine1(address.getLine1());
        order.setShippingLine2(address.getLine2());
        order.setShippingCity(address.getCity());
        order.setShippingState(address.getState());
        order.setShippingPostalCode(address.getPostalCode());
        order.setShippingCountry(address.getCountry());
        order.setShippingPhone(address.getPhone());

        for (CartItem cartItem : cartItems) {
            Inventory inventory = inventoryByProduct.get(cartItem.getProduct().getId());
            Product product = inventory.getProduct();
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setSku(product.getSku());
            orderItem.setUnitPrice(product.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setLineTotal(pricingService.lineTotal(product.getPrice(), cartItem.getQuantity()));
            order.addItem(orderItem);
        }
        orderRepository.saveAndFlush(order);

        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            Inventory inventory = inventoryByProduct.get(entry.getKey());
            int previous = inventory.getQuantity();
            int requested = entry.getValue();
            int remaining = previous - requested;
            inventory.setQuantity(remaining);
            inventoryRepository.save(inventory);
            recordInventoryTransaction(
                    inventory.getProduct(),
                    InventoryTransactionType.ORDER_DEDUCTION,
                    -requested,
                    previous,
                    remaining,
                    "Order inventory allocation",
                    order.getId(),
                    user
            );
        }

        cartItemRepository.deleteAll(cartItems);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listOrders(
            UUID userId,
            OrderStatus status,
            int page,
            int size
    ) {
        Specification<Order> specification = OrderSpecifications.userId(userId);
        if (specification == null) {
            throw ApiException.badRequest("INVALID_CUSTOMER", "Customer is required");
        }
        if (status != null) {
            specification = specification.and(OrderSpecifications.status(status));
        }
        Page<Order> orders = orderRepository.findAll(specification, pageable(page, size));
        return PageResponse.from(orders.map(OrderSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> adminList(
            OrderStatus status,
            UUID userId,
            int page,
            int size
    ) {
        Specification<Order> specification = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            specification = specification.and(OrderSpecifications.status(status));
        }
        if (userId != null) {
            specification = specification.and(OrderSpecifications.userId(userId));
        }
        Page<Order> orders = orderRepository.findAll(specification, pageable(page, size));
        return PageResponse.from(orders.map(AdminOrderSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse adminGet(UUID orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelByCustomer(UUID userId, UUID orderId) {
        Order order = lockOrder(orderId);
        requireOwner(order, userId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponse.from(order);
        }
        if (!CUSTOMER_CANCELLABLE.contains(order.getStatus())) {
            throw ApiException.conflict(
                    "ORDER_NOT_CANCELLABLE",
                    "Orders can only be cancelled before processing begins"
            );
        }
        return cancelLocked(order, userId);
    }

    @Transactional
    public OrderResponse updateStatusByAdmin(UUID actorId, UUID orderId, OrderStatus targetStatus) {
        Order order = lockOrder(orderId);
        if (targetStatus == OrderStatus.CANCELLED) {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                return OrderResponse.from(order);
            }
            if (!ADMIN_CANCELLABLE.contains(order.getStatus())) {
                throw ApiException.conflict(
                        "ORDER_NOT_CANCELLABLE",
                        "Shipped or delivered orders cannot be cancelled"
                );
            }
            return cancelLocked(order, actorId);
        }
        if (order.getStatus() == targetStatus) {
            throw ApiException.conflict("ORDER_STATUS_UNCHANGED", "Order already has status " + targetStatus);
        }
        OrderStateMachine.requireTransition(order.getStatus(), targetStatus);
        order.setStatus(targetStatus);
        orderRepository.saveAndFlush(order);
        return OrderResponse.from(order);
    }

    private OrderResponse cancelLocked(Order order, UUID actorId) {
        User actor = userRepository.getReferenceById(actorId);
        refundOrFailPayments(order, actor);
        restoreInventory(order, actor);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        orderRepository.saveAndFlush(order);
        return OrderResponse.from(order);
    }

    private void refundOrFailPayments(Order order, User actor) {
        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                PaymentProvider.RefundResult refund = paymentProvider.refund(payment);
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setRefundReference(refund.refundReference());
                payment.setRefundedAt(Instant.now());
                paymentRepository.save(payment);
            } else if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Order cancelled before payment completed");
                paymentRepository.save(payment);
            }
        }
    }

    private void restoreInventory(Order order, User actor) {
        Map<UUID, Integer> quantityByProduct = new LinkedHashMap<>();
        List<UUID> productIds = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() == null) {
                continue;
            }
            UUID productId = item.getProduct().getId();
            if (!quantityByProduct.containsKey(productId)) {
                productIds.add(productId);
            }
            quantityByProduct.merge(productId, item.getQuantity(), Integer::sum);
        }
        productIds.sort(UUID::compareTo);
        List<Inventory> inventories = inventoryRepository.findAllByProductIdInForUpdate(productIds);
        Map<UUID, Inventory> byProduct = new HashMap<>();
        for (Inventory inventory : inventories) {
            byProduct.put(inventory.getProduct().getId(), inventory);
        }
        for (UUID productId : productIds) {
            Inventory inventory = byProduct.get(productId);
            if (inventory == null) {
                throw ApiException.conflict(
                        "INVENTORY_NOT_FOUND",
                        "Cannot restore inventory because a product inventory record is missing"
                );
            }
            int quantity = quantityByProduct.get(productId);
            int previous = inventory.getQuantity();
            int restored = previous + quantity;
            inventory.setQuantity(restored);
            inventoryRepository.save(inventory);
            recordInventoryTransaction(
                    inventory.getProduct(),
                    InventoryTransactionType.ORDER_RESTOCK,
                    quantity,
                    previous,
                    restored,
                    "Order cancellation",
                    order.getId(),
                    actor
            );
        }
    }

    private void validateLineForCheckout(CartItem cartItem, Inventory inventory) {
        Product product = inventory.getProduct();
        if (!product.isPurchasable()) {
            throw ApiException.conflict(
                    "PRODUCT_UNAVAILABLE",
                    "Product " + product.getName() + " is no longer available"
            );
        }
        if (inventory.getQuantity() < cartItem.getQuantity()) {
            throw ApiException.conflict(
                    "INSUFFICIENT_STOCK",
                    "Only " + inventory.getQuantity() + " unit(s) of " + product.getName() + " are available"
            );
        }
    }

    private void recordInventoryTransaction(
            Product product,
            InventoryTransactionType type,
            int delta,
            int previous,
            int current,
            String reason,
            UUID referenceId,
            User actor
    ) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setProduct(product);
        transaction.setTransactionType(type);
        transaction.setDelta(delta);
        transaction.setPreviousQuantity(previous);
        transaction.setNewQuantity(current);
        transaction.setReason(reason);
        transaction.setReferenceId(referenceId);
        transaction.setPerformedBy(actor);
        inventoryTransactionRepository.save(transaction);
    }

    private Order lockOrder(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
    }

    private void requireOwner(Order order, UUID userId) {
        if (!order.getUser().getId().equals(userId)) {
            throw ApiException.notFound("ORDER_NOT_FOUND", "Order not found");
        }
    }

    private Pageable pageable(int page, int size) {
        if (page < 0) {
            throw ApiException.badRequest("INVALID_PAGE", "page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("INVALID_PAGE_SIZE", "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
