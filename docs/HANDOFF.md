# NexaCart Engineering Handoff

## 1. Delivered scope

NexaCart is a complete e-commerce REST API implemented as a modular Spring Boot monolith. Delivered functionality includes:

- Customer registration, login, server-side logout/revocation, profile management, ADMIN/CUSTOMER authorization, admin account management, and login/registration rate limiting.
- Category and product administration, SKU and slug uniqueness, product deactivation, public browsing rules, search by name/description/SKU, category and price filters, availability filtering, sorting, and pagination.
- One server-side cart per customer, server-priced totals, quantity validation, stock checks, and inactive-product handling.
- Inventory initialization, administrative adjustments with mandatory reasons, low-stock tracking, transaction history, pessimistic stock locking, and cancellation restocks.
- Address CRUD with ownership checks, a single-default-address invariant, and shipping-address snapshots on orders.
- Transactional order creation from the cart, database-backed idempotency, immutable item/price snapshots, validated status transitions, customer/admin cancellation rules, and refund/inventory restoration flows.
- Payment-provider abstraction, development-only simulated payments, one pending payment per order, idempotent completion, payment history, refunds, and duplicate-notification safety.
- Verified-purchase reviews, 1–5 ratings, ownership-protected updates/deletes, average-rating summaries, and duplicate-review prevention.
- Flyway-managed PostgreSQL schema, Hibernate schema validation, centralized exception handling, OpenAPI/Swagger, Actuator, Docker Compose, reproducible dev seed data, and a Postman collection.

## 2. Architecture, technology, and layout

**Architecture:** layered modular monolith organized by business domain. Controllers handle HTTP only; services own business logic and transactions; repositories own persistence; DTO records define external contracts; JPA entities are never serialized.

```
src/main/java/com/nexacart/
├── auth/ user/ category/ product/ inventory/
├── cart/ address/ order/ payment/ review/
├── common/           Base entity, pricing, errors, pagination, utilities
├── config/           Properties, security wiring, OpenAPI, dev seeding
└── security/         JWT issuing/validation, error handlers, rate limiting
```

**Verified stack:**

- Java 21 target. The application container runs Java 21; local tests were run on Java 25.
- Spring Boot 4.1.2 / Spring Framework 7.0.x.
- Spring Security 7.1.x with Nimbus JWT support.
- Spring Data JPA / Hibernate 7.4.13.Final.
- PostgreSQL 17.11, Flyway 12.4.0.
- springdoc-openapi 3.2.0.
- Testcontainers 2.0.5, JUnit 6.0.x, Mockito 5.23.x.
- Maven 3.9.16, Docker 29.1.3, Compose v5.5.1.

**Core tools and references:**

- `README.md` — setup, configuration, API overview, examples, security, limitations.
- `docs/ARCHITECTURE.md` — module map, ER diagram, order workflow, locking/idempotency decisions.
- `docs/DEVELOPMENT.md` — development conventions, testing, and extension guide.
- `docs/nexacart.postman_collection.json` — executable API collection.
- `Dockerfile`, `compose.yaml`, `.env.example`, `.gitignore`.
- `src/main/resources/db/migration/V1__initial_schema.sql`.

## 3. Security and data-integrity measures

**Authentication and authorization**

- BCrypt cost 12; password hashes are never returned or logged.
- Short-lived HMAC-SHA256 access tokens with signature, issuer, and expiration validation.
- Real logout via a revoked-token (`jti`) database denylist, not a no-op endpoint.
- Current account state and role are read from PostgreSQL on each request; disabling an account or changing a role takes effect immediately.
- URL-level and method-level authorization; public registration cannot assign ADMIN.
- CORS uses explicit configured origins. Actuator exposes health/info only, with health details hidden.
- Login/registration requests are rate-limited per IP; sensitive credentials and bearer tokens are not logged.

**Commerce integrity**

- All prices and totals are calculated server-side with `BigDecimal` scale 2 (`NUMERIC(12,2)`).
- Checkout locks inventory rows with `PESSIMISTIC_WRITE` in ascending product-ID order, preventing overselling and multi-item deadlocks.
- Order creation is one transaction spanning order, item snapshots, stock deduction, inventory ledger, and cart clearing.
- A unique `(user_id, idempotency_key)` constraint plus cart-row locking makes order retries idempotent; duplicated attempts cannot deduct stock twice.
- Order items preserve product name, SKU, and unit price. Orders preserve a shipping-address snapshot, so later catalog/address edits do not rewrite history.
- Database constraints enforce unique SKUs/emails/payment references, one pending payment per order, one default address per customer, one review per customer/product, non-negative stock/totals, and ratings 1–5.
- Payments use provider references, row locks, terminal-state checks, and a partial unique index to make duplicate notifications safe.
- Cancellation restores inventory exactly once and refunds successful simulated payments; shipped/delivered orders cannot be cancelled.
- Reviews require a verified `DELIVERED` purchase; eligibility is checked against order history rather than trusted from the client.
- Customer-owned resources are queried by owner ID; cross-customer access returns 404 to avoid enumeration.

## 4. Verification, build, test, and run commands

```bash
# Full clean build and test (Testcontainers requires Docker)
mvn clean verify

# Package without tests
mvn -DskipTests package

# Local development with PostgreSQL container
docker compose up -d postgres
export NEXACART_JWT_SECRET="$(openssl rand -base64 48)"
export NEXACART_ADMIN_PASSWORD="AdminDevPass123!"
export NEXACART_SEED_CUSTOMER_PASSWORD="Customer123!"
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# Full containerized environment
cp .env.example .env
sed -i "s|^NEXACART_JWT_SECRET=.*|NEXACART_JWT_SECRET=$(openssl rand -base64 48)|" .env
docker compose up --build
```

**Verified results:**

- `mvn clean verify`: **BUILD SUCCESS — 59 tests, 0 failures, 0 errors, 0 skipped**.
- Tests validated Flyway migration and Hibernate schema validation against real PostgreSQL 17 via Testcontainers.
- Concurrency tests verified last-unit oversell prevention and concurrent same-idempotency-key checkout.
- Docker image built successfully with Java 21.
- Docker Compose started PostgreSQL and the application; both reached healthy status.
- Fresh-volume startup verified Flyway, seeding, login, catalog retrieval, Swagger/OpenAPI, and a complete order → payment → delivery → review smoke flow.
- Final clean seed state verified: 3 users, 4 categories, 12 products, and no transactional data.

**Local endpoints when running:**

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

## 5. Important implementation decisions

- **Pessimistic inventory locking** was selected over optimistic retries because checkout must atomically validate and mutate multiple stock rows and record exact previous/new quantities for the audit ledger.
- **Stock is deducted at order creation**, which prevents overselling while payment is pending. The documented trade-off is that abandoned pending orders hold stock until cancelled; an expiration job is a future improvement.
- **Cart rows are locked before idempotency re-check and inventory locks**, serializing duplicate retries and cart mutations.
- **Checkout loads cart items without fetching inventory entities before the lock query.** This avoids stale Hibernate version conflicts when another transaction commits a stock update between cart read and row lock.
- **JWT role claims are not used for authorization.** Authorities are resolved from the current database account, closing the role-change window without issuing new tokens.
- **Order `discountAmount` is persisted but currently always zero** because no promotion rules exist; totals remain internally consistent.
- **Payments are simulated by design.** The simulator is marked as such in every response (`provider: SIMULATED`), accepts no card data, is disabled by default, and is blocked under `prod`/`production`.
- **PostgreSQL is the only test database** for integration tests. H2 was intentionally avoided because it cannot validate PostgreSQL locking, partial indexes, or constraint behavior.

## 6. Limitations and next steps

- No real payment provider integration yet. Production payments require a provider adapter, signed raw-body webhooks, provider event deduplication, asynchronous refunds, and reconciliation.
- No refresh tokens, email verification, password reset, MFA, or email/SMS notifications.
- No scheduled expiration for unpaid pending orders; stock remains reserved until cancellation.
- Rate limiting is in-memory and per-instance; distributed deployments need Redis or an API gateway.
- No discount/promotion engine, product variants, per-warehouse stock, or admin review moderation.
- Search uses PostgreSQL trigram-indexed `LIKE`, not a dedicated search engine.
- Production profile expects externally supplied database credentials and JWT secret. It has not been exercised against a real external payment provider or deployment platform.
- No CI workflow is committed; recommended next step is a GitHub Actions pipeline running `mvn verify`, Docker build, and dependency/security scanning.
