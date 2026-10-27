# NexaCart Development Guide

This is the contribution guide for humans and AI agents: local workflow, conventions, migrations, tests, and common pitfalls. For layout and ownership, see [Project structure](PROJECT_STRUCTURE.md). For runtime configuration and profiles, see [Configuration and operations](OPERATIONS.md). For system design, see [Architecture](ARCHITECTURE.md).

## 1. Local workflow

```bash
docker compose up -d postgres
export NEXACART_JWT_SECRET="$(openssl rand -base64 48)"
export NEXACART_ADMIN_PASSWORD="ChangeMe_Admin_Dev_123!"
export NEXACART_SEED_CUSTOMER_PASSWORD="Customer123!"
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

For a complete containerized environment:

```bash
cp .env.example .env
sed -i "s|^NEXACART_JWT_SECRET=.*|NEXACART_JWT_SECRET=$(openssl rand -base64 48)|" .env
docker compose up --build
```

Run tests with Docker available:

```bash
mvn test
mvn clean verify
```

Useful URLs:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

## 2. Profiles

Profile behavior and environment variables are documented in [Configuration and operations](OPERATIONS.md#profiles). Use the `dev` profile for seeded data and the payment simulator, and the `test` profile for integration tests. Do not enable the simulator or seeding in a production profile.

## 3. Adding a feature

Follow the existing domain package shape:

```
domain/
├── controller/   HTTP mapping and status codes only
├── dto/          request/response records + validation
├── entity/       JPA persistence model (never returned directly)
├── repository/   Spring Data interfaces and specifications
└── service/      business rules and @Transactional boundaries
```

Recommended checklist:

1. Model the database change as a **new** Flyway migration (`V2__...sql`, `V3__...sql`). Never edit an applied migration.
2. Update the JPA entity to match. Run the context/integration tests with `ddl-auto=validate`.
3. Add database constraints for invariants whenever possible.
4. Add request DTO validation and map explicitly in the service or DTO factory.
5. Keep ownership checks in services, not only controllers.
6. Add a `@PreAuthorize` check for administrative operations in addition to URL rules.
7. Return proper HTTP status codes; use `ApiException` for business failures.
8. Add tests for both success and failure paths, including cross-customer access.
9. Update the API contract documentation in `docs/API.md` and the Postman collection when endpoints or payloads change.
10. Update `docs/ARCHITECTURE.md`, `docs/OPERATIONS.md`, or `docs/PROJECT_STRUCTURE.md` if the change affects boundaries, configuration, or layout.
11. Update `docs/README.md` if you add or rename a documentation file.

### Naming conventions

- REST paths: plural nouns; `/api/v1/admin/...` for administrative resources.
- JSON: lower camel case.
- Database: snake case.
- Enum values: upper snake case.
- Money: `BigDecimal` + scale 2; never `double`.
- Time: `Instant`; never local-zone timestamps in persistence.
- Error codes: stable upper snake case, e.g. `INSUFFICIENT_STOCK`.

## 4. Transaction boundaries

- Put `@Transactional` on service methods that mutate state.
- Read-only queries use `@Transactional(readOnly = true)`.
- Do not call external services inside database transactions when a real integration can be asynchronous. The simulated refund is synchronous because it is local-only; a real provider should use a saga/outbox or a clear compensation workflow.
- Order creation uses `TransactionTemplate` deliberately. It must catch the unique-key race **after** the transaction rollback, then load the winner's committed order.
- Never load an inventory entity before acquiring its row lock in checkout. Use `CartItemRepository.findForCheckout` (product proxies only), then `InventoryRepository.findAllByProductIdInForUpdate`.

## 5. Inventory checklist

Any new stock-changing operation must:

1. Lock the inventory row(s) pessimistically.
2. Lock rows in ascending product-ID order.
3. Validate the entire operation before changing quantities.
4. Update the inventory balance.
5. Insert an `InventoryTransaction` with previous/new quantity, delta, type, reason, reference, and actor.
6. Ensure the operation is idempotent or guarded by a state transition so retries cannot apply it twice.
7. Add a test that verifies final stock and ledger count.

Existing transaction types:

- `INITIALIZATION` — product creation.
- `ADMIN_ADJUSTMENT` — administrator correction.
- `ORDER_DEDUCTION` — checkout allocation/reservation.
- `ORDER_RESTOCK` — qualifying cancellation.

## 6. Idempotency rules

### Orders

- API requires `Idempotency-Key`.
- Scope: per authenticated customer.
- Storage: `orders(user_id, idempotency_key)` unique.
- Retry: exact same customer + key returns the original order.
- Use a new key for a new intended order. Different payloads with the same key intentionally return the original order, matching standard idempotency semantics.
- Under concurrency, the cart lock serializes attempts and the unique constraint is the final defense.

### Payments

- One pending payment per order (partial unique index).
- Provider reference is globally unique.
- Completion is state-guarded and row-locked.
- Repeated success returns the existing success; conflicting terminal transitions return 409.
- Pending payments are failed on cancellation.

## 7. Testing guide

### Base class

`com.nexacart.support.IntegrationTestBase`:

- Starts one PostgreSQL 17 Testcontainer for the JVM.
- Provides `@SpringBootTest` + `@AutoConfigureMockMvc`.
- Activates `test`.
- Truncates all domain tables before each test.
- Provides helpers for registration, login, admin creation, categories, products, addresses, carts, and orders.

### Adding a test

- Put unit tests under `common`, `security`, or the relevant domain without a Spring context.
- Extend `IntegrationTestBase` for anything that touches database behavior or HTTP security.
- Use unique email/SKU values or rely on the truncation isolation.
- Assert failure codes, not only status codes.
- For concurrency, call services from multiple threads with a `CountDownLatch`. Use separate transactions; do not annotate the test with `@Transactional`.
- Testcontainers requires a running Docker daemon. There is intentionally no H2 fallback.

Test layout:

```
src/test/java/com/nexacart/
├── auth/AuthIntegrationTest.java
├── product/ProductCatalogIntegrationTest.java
├── cart/CartAndAddressIntegrationTest.java
├── order/OrderIntegrationTest.java
├── order/service/OrderStateMachineTest.java
├── payment/PaymentIntegrationTest.java
├── review/ReviewIntegrationTest.java
├── inventory/InventoryIntegrationTest.java
├── security/AuthRateLimitFilterTest.java, SecurityIntegrationTest.java
├── common/pricing/PricingServiceTest.java
└── support/IntegrationTestBase.java, ApplicationContextIntegrationTest.java
```

Unit tests live in the package of the class under test; integration tests live in their domain package and extend `IntegrationTestBase`.

## 8. Database development

- Start only PostgreSQL: `docker compose up -d postgres`.
- Inspect schema: `docker compose exec postgres psql -U nexacart -d nexacart`.
- Reset local data: `docker compose down -v` (destructive).
- Hibernate logs SQL only at `org.hibernate.SQL=DEBUG`; the dev profile leaves this at WARN.
- Partial indexes are intentionally not representable in JPA annotations; Flyway owns them. Hibernate validation checks tables/columns/types, not every index.
- `pg_trgm` must exist. The Docker image allows the migration to create it; a restricted managed database may require a DBA to enable it first.

Useful SQL checks:

```sql
SELECT user_id, quantity FROM carts JOIN cart_items ON carts.id = cart_items.cart_id;
SELECT product_id, quantity, version FROM inventory ORDER BY product_id;
SELECT order_id, transaction_type, delta, previous_quantity, new_quantity
FROM inventory_transactions ORDER BY created_at;
SELECT order_id, status, provider_reference FROM payments;
```

## 9. Security development rules

- Never log request bodies containing passwords or Authorization headers.
- Never add a public endpoint that mutates catalog/inventory/order state.
- Never accept client-side prices, totals, or roles.
- Every customer-owned resource lookup must include the owner in the query (`findByIdAndUserId`) or perform an equivalent service check.
- Return 404 rather than 403 for another customer's private resource to avoid enumeration.
- Add DB constraints for uniqueness and invariants; do not rely solely on service checks.
- Keep the JWT secret out of source control.
- If adding refresh tokens or cookies, redesign CSRF and revocation handling.
- If enabling the payment simulator outside local development, stop: it is intentionally blocked in `prod`/`production` and must not be relabeled as a real payment result.

## 10. Extending to a real payment provider

1. Implement `PaymentProvider` with provider-specific initiation and refund calls.
2. Create the provider bean only under an appropriate profile/configuration.
3. Persist provider transaction IDs and amounts; never card data.
4. Add signed webhook handling with raw-body signature verification.
5. Add a `payment_provider_events` table with a unique provider event ID to deduplicate notifications.
6. Process webhook state changes idempotently under row locks.
7. Use asynchronous/compensating workflows for refunds and downstream order changes.
8. Update the simulator guard and docs, and add provider integration tests using the provider's official test mode.

## 11. Common pitfalls

- **Editing an applied Flyway migration:** causes checksum validation failure. Add a new migration instead.
- **Comparing `BigDecimal` with `equals`:** use `compareTo` for monetary comparisons.
- **Loading stock before locking:** can trigger Hibernate conflicting-version errors and stale validation.
- **Using unsigned logic for stock:** reject negative results before saving; the DB check is a backstop.
- **Forgetting to restore stock on cancellation:** always add an `ORDER_RESTOCK` ledger row and test the count.
- **Returning entities from controllers:** creates lazy-loading and data-exposure problems. Map to response records.
- **Trusting JWT role claims after role changes:** the converter reads the current DB role; preserve that behavior.
- **Disabling tests to get a green build:** failure-path tests are part of the product contract.

## 12. Build verification

```bash
mvn clean verify
```

Expected: the full unit and integration suite passes, Flyway migrations apply to a fresh PostgreSQL Testcontainer, the Spring context starts with schema validation, and a runnable Boot JAR is produced at `target/nexacart-1.0.0.jar`.

The same command runs in CI with JDK 21, followed by a container-image build. CI is defined in `.github/workflows/ci.yml`.

Docker verification:

```bash
docker compose build
docker compose up -d
curl http://localhost:8080/actuator/health
```
