# NexaCart

NexaCart is a production-minded e-commerce REST API built with Java and Spring Boot. It covers customer accounts, JWT authentication, a product catalog, cart, inventory control, addresses, transactional order processing, simulated payments, and verified-purchase reviews.

The project is deliberately a modular monolith. It is easy to run locally, but the important business rules are implemented for real: server-side pricing, row-level inventory locking, database-backed order idempotency, immutable order snapshots, validated status transitions, and ownership checks on customer resources.

## Project goals

- Demonstrate professional Java/Spring backend engineering, not a CRUD-only tutorial.
- Keep business rules explicit and testable.
- Treat inventory and order integrity as core requirements.
- Use PostgreSQL semantics rather than hiding them behind an embedded database.
- Provide realistic security defaults and clear documentation of limitations.
- Remain straightforward to run with Maven or Docker Compose.

## Features

### Authentication and users
- Customer registration, login, server-side logout, and profile management.
- BCrypt password hashing (cost 12).
- Short-lived HMAC-SHA256 JWT access tokens issued with Spring Security's Nimbus-based support.
- Real token revocation through a database denylist checked on every request.
- Account status and current role are checked from the database on every request, so disabling an account or changing a role takes effect immediately.
- ADMIN and CUSTOMER roles, URL-level and method-level authorization.
- Administrators can list users, view one user, and change role/enabled status. The last enabled administrator and self-demotion/self-disable are protected.
- In-memory IP rate limiting on login and registration.

### Catalog
- Category and product administration.
- SKU uniqueness (case-insensitive), deterministic slugs, price and status validation.
- Public browsing excludes inactive products and inactive categories.
- Search by name, description, or SKU; filters for category, price range, and availability; allow-listed sorting and pagination.
- PostgreSQL indexes, including trigram GIN indexes for catalog search.
- Product deactivation and category deactivation.

### Cart
- One server-side cart per customer.
- Add, update, remove, and clear operations.
- Stock and product availability validation at cart time and again at checkout.
- Server-side prices and totals; client-submitted prices are ignored.
- Inactive products remain visible in existing carts but make the cart not checkout-ready.
- Precise `BigDecimal` money calculations with scale 2.

### Inventory
- Inventory record initialized with each product.
- Pessimistic row locks in deterministic order during checkout/cancellation to prevent overselling and deadlocks.
- Administrative delta or absolute-quantity adjustments with a mandatory reason.
- Low-stock thresholds and low-stock listing.
- Immutable inventory transaction history for initialization, manual adjustments, order allocation, and cancellation restocks.
- Database checks prevent negative stock.

### Orders
- Transactional order creation from the cart.
- Required `Idempotency-Key` header, stored under a unique `(user_id, idempotency_key)` constraint; retries return the original order without re-deducting inventory.
- Product name, SKU, and unit price snapshots.
- Shipping address snapshots.
- Subtotal, shipping, discount field, and total calculated on the server.
- Defined state machine: `PENDING -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED`, with cancellation before shipment.
- Customer ownership enforcement.
- Customer cancellation for `PENDING`/`CONFIRMED`; administrative cancellation for `PENDING`/`CONFIRMED`/`PROCESSING`.
- Inventory is restored exactly once on qualifying cancellation.
- Administrative order listing and status transitions.

### Addresses
- Per-customer address CRUD.
- At most one default address per customer, enforced by a partial unique index.
- Address ownership enforcement.
- Orders retain shipping snapshots, so later address edits do not affect historical orders.

### Payments
- Provider abstraction (`PaymentProvider`) with a development-only simulator implementation.
- One pending payment per order, enforced by a partial unique index.
- Unique provider references and idempotent completion processing.
- Payment history by order.
- Payment success transitions a `PENDING` order to `CONFIRMED`.
- Cancellation refunds successful simulated payments and closes pending ones.
- Simulation is rejected when the `prod`/`production` profile is active, regardless of property configuration.

### Reviews
- Reviews are restricted to customers with a `DELIVERED` order containing the product. Verification is enforced by a database purchase query, not a client flag.
- One review per customer/product, rating 1–5, optional title/comment.
- Public review listing with calculated average rating and review count.
- Owners can update or delete their reviews; other customers cannot.

### Operations
- Flyway migrations, Hibernate schema validation.
- Actuator health/info endpoints.
- OpenAPI/Swagger UI with bearer authentication documentation.
- Development seed data with configurable credentials.
- Dockerfile and Docker Compose with PostgreSQL health checks and persistent storage.
- 59 automated tests, including Testcontainers integration tests and concurrency tests.

## Technology stack

| Technology | Version / notes |
|---|---|
| Java | 21 target bytecode; developed and tested on Java 25 as well |
| Maven | 3.9.16 |
| Spring Boot | 4.1.2 |
| Spring Framework | 7.0.x |
| Spring Security | 7.1.x, Nimbus JOSE JWT support |
| Spring Data JPA / Hibernate | 7.4.13.Final |
| PostgreSQL | 17 (Docker image `postgres:17-alpine`) |
| Flyway | 12.4.0 |
| springdoc-openapi | 3.2.0 |
| Testcontainers | 2.0.5 |
| JUnit / Mockito | JUnit 6.0.x / Mockito 5.23.x |
| Lombok | 1.18.48 (entities/configuration only) |
| Docker / Compose | Verified with Docker 29.1.3 and Compose v5.5.1 |

Versions are managed by the Spring Boot 4.1.2 BOM except springdoc-openapi, which is pinned explicitly.

## Architecture

The application is a layered modular monolith organized by business domain:

```
HTTP -> controller -> service -> repository -> PostgreSQL
                 \-> DTOs / validation
                 \-> domain entities (never serialized)
```

Cross-cutting concerns live in `config`, `security`, and `common`. Domain packages own their controllers, DTOs, entities, repositories, and services. Orders use inventory `PESSIMISTIC_WRITE` locks ordered by product ID. Flyway owns the schema and Hibernate only validates it.

See [Architecture](docs/ARCHITECTURE.md) for diagrams, transaction boundaries, the order workflow, and design trade-offs.

### Directory structure

```
NexaCart/
├── compose.yaml
├── Dockerfile
├── .env.example
├── pom.xml
├── README.md
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DEVELOPMENT.md
│   └── nexacart.postman_collection.json
└── src/
    ├── main/
    │   ├── java/com/nexacart/
    │   │   ├── NexaCartApplication.java
    │   │   ├── auth/
    │   │   ├── user/
    │   │   ├── category/
    │   │   ├── product/
    │   │   ├── inventory/
    │   │   ├── cart/
    │   │   ├── address/
    │   │   ├── order/
    │   │   ├── payment/
    │   │   ├── review/
    │   │   ├── common/
    │   │   ├── config/
    │   │   └── security/
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/migration/V1__initial_schema.sql
    └── test/
        ├── java/com/nexacart/... (unit + Testcontainers integration tests)
        └── resources/application-test.yml
```

## Prerequisites

- JDK 21 or newer. The build targets Java 21.
- Maven 3.9+.
- Docker Engine with a running daemon (for PostgreSQL, Testcontainers, and containerized runs).
- Docker Compose v2+.
- `openssl` if you use the suggested secret-generation command.

Check the local toolchain:

```bash
java -version
mvn -version
docker --version
docker compose version
```

## Installation

```bash
git clone <repository-url> NexaCart
cd NexaCart
mvn -q -DskipTests package
```

## Environment variables

Copy the example environment file and replace the JWT secret:

```bash
cp .env.example .env
sed -i "s|^NEXACART_JWT_SECRET=.*|NEXACART_JWT_SECRET=$(openssl rand -base64 48)|" .env
```

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `NEXACART_JWT_SECRET` | Yes | none | Base64-encoded HMAC key, at least 32 bytes. Generate with `openssl rand -base64 48`. |
| `NEXACART_JWT_ISSUER` | No | `nexacart` | JWT issuer and issuer validation. |
| `NEXACART_JWT_TTL_SECONDS` | No | `900` | Access token lifetime in seconds. |
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/nexacart` | JDBC URL. Compose sets this to the `postgres` service. |
| `DB_USER` | No | `nexacart` | Database user. |
| `DB_PASSWORD` | No | `nexacart_dev_password` | Database password. Use a real secret outside local development. |
| `NEXACART_CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000,http://localhost:5173` | Comma-separated explicit frontend origins. |
| `NEXACART_SHIPPING_FEE` | No | `5.99` | Flat shipping fee. |
| `NEXACART_FREE_SHIPPING_THRESHOLD` | No | `100.00` | Free-shipping subtotal threshold. |
| `NEXACART_CURRENCY` | No | `USD` | ISO currency code stored on orders/payments. |
| `NEXACART_PAYMENT_SIMULATOR_ENABLED` | No | `false` (`true` in dev) | Enables the development payment simulator. Ignored in `prod`/`production`. |
| `NEXACART_RATE_LIMIT_ENABLED` | No | `true` | Enables auth endpoint rate limiting. |
| `NEXACART_RATE_LIMIT_MAX_ATTEMPTS` | No | `10` | Requests per IP and endpoint per window. |
| `NEXACART_RATE_LIMIT_WINDOW_SECONDS` | No | `60` | Fixed window length. |
| `NEXACART_SEED_ENABLED` | No | `false` (`true` in dev) | Enables the dev-only seeder. |
| `NEXACART_SEED_CUSTOMER_PASSWORD` | No | `Customer123!` in dev | Password for seeded customers. Development only. |
| `NEXACART_ADMIN_EMAIL` | No | `admin@nexacart.local` | Development administrator email. |
| `NEXACART_ADMIN_PASSWORD` | No | empty | If set in dev, creates the administrator. Never set a real admin password in source control. |
| `NEXACART_OPENAPI_ENABLED` | No | `false` in prod | Enables Swagger/OpenAPI in the production profile. |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_PORT`, `APP_PORT` | Compose only | dev values | Compose database and port configuration. |

The application refuses to start without a valid JWT secret. Never commit `.env` or real credentials.

## Database setup and migrations

Flyway runs automatically at startup from `src/main/resources/db/migration`. The initial migration creates the normalized schema, constraints, partial unique indexes, and search indexes. It enables the PostgreSQL `pg_trgm` extension for catalog search.

Hibernate is configured with `ddl-auto: validate`; it will not modify the schema. Flyway `clean` is disabled.

Start only PostgreSQL with Compose:

```bash
docker compose up -d postgres
docker compose ps
```

The Compose volume `nexacart_pgdata` persists data across restarts. To reset local data intentionally:

```bash
docker compose down -v
```

## Running the application

### Option A: Maven + local PostgreSQL container

```bash
docker compose up -d postgres
export NEXACART_JWT_SECRET="$(openssl rand -base64 48)"
export NEXACART_ADMIN_PASSWORD="AdminDevPass123!"
export NEXACART_SEED_CUSTOMER_PASSWORD="Customer123!"
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

The application starts at `http://localhost:8080`. The `dev` profile enables the simulator and idempotent sample data seeding.

### Option B: Full Docker Compose stack

```bash
cp .env.example .env
sed -i "s|^NEXACART_JWT_SECRET=.*|NEXACART_JWT_SECRET=$(openssl rand -base64 48)|" .env
docker compose up --build
```

Stop the stack:

```bash
docker compose down
```

Stop and remove the local database volume:

```bash
docker compose down -v
```

The app container waits for a healthy PostgreSQL instance. Both services have health checks; PostgreSQL data is persisted in a named volume.

### Option C: Packaged JAR

```bash
mvn -DskipTests package
export NEXACART_JWT_SECRET="$(openssl rand -base64 48)"
java -jar target/nexacart-1.0.0.jar
```

Without a `dev` profile, seeding and the payment simulator remain disabled.

## Running tests

The test suite uses PostgreSQL 17 through Testcontainers. Docker must be running.

```bash
mvn test
```

Or a full build:

```bash
mvn clean verify
```

Verified locally: **59 tests, 0 failures, 0 errors**, including:

- Unit tests for pricing and order state transitions.
- Auth rate-limit filter tests.
- Spring context and Flyway schema validation.
- Registration/login/logout/revocation, disabled-account checks, role changes, and authorization.
- Catalog filtering, sorting, pagination, visibility rules, and duplicate SKU handling.
- Cart totals, server-side pricing, stock validation, isolation, and inactive products.
- Address defaults and ownership.
- Order creation snapshots, idempotent retries, concurrent idempotent requests, last-unit oversell prevention, cancellation restocking, ownership, and invalid transitions.
- Payment initiation, idempotent success, failure/retry, refunds, and duplication safety.
- Verified-purchase review eligibility and average rating updates.
- Inventory adjustments/history, negative-stock rejection, and admin authorization.

Some tests intentionally call domain services concurrently to exercise real row locking and transaction behavior against PostgreSQL.

If Docker is unavailable in a given environment, the integration tests cannot run; there is deliberately no H2 fallback, because H2 would not validate PostgreSQL locking and constraint behavior.

## Swagger UI and OpenAPI

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`
- Actuator info (authenticated): `http://localhost:8080/actuator/info`

Use the **Authorize** button in Swagger UI with a token from the login endpoint. In `prod`/`production`, OpenAPI is disabled by default and can only be enabled explicitly with `NEXACART_OPENAPI_ENABLED=true`.

## Authentication workflow

1. `POST /api/v1/auth/register` creates a CUSTOMER account. Public registration ignores any attempted role.
2. `POST /api/v1/auth/login` returns a short-lived JWT access token.
3. Send `Authorization: Bearer <token>` on protected requests.
4. `POST /api/v1/auth/logout` stores the token's `jti` in the `revoked_tokens` table. The JWT validator rejects revoked tokens on subsequent requests. Expired denylist rows are removed hourly.
5. Disabling an account or changing a role takes effect on the next request because the validator and authority converter consult current database state.
6. There are no refresh tokens; clients must log in again after expiry.

Example:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer1@nexacart.local","password":"Customer123!"}'
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "expiresAt": "2026-10-27T02:00:00Z",
  "user": {
    "id": "f53e...",
    "email": "customer1@nexacart.local",
    "firstName": "Maya",
    "lastName": "Hassan",
    "role": "CUSTOMER",
    "enabled": true,
    "createdAt": "2026-10-27T01:30:00Z"
  }
}
```

## API overview

All routes are versioned under `/api/v1`.

| Area | Method and path | Access |
|---|---|---|
| Auth | `POST /auth/register`, `POST /auth/login` | Public |
| Auth | `POST /auth/logout` | Authenticated |
| Users | `GET/PATCH /users/me` | Authenticated |
| Admin users | `GET /admin/users`, `GET/PATCH /admin/users/{id}` | ADMIN |
| Categories | `GET /categories`, `GET /categories/{id}` | Public (active only) |
| Admin categories | `GET /admin/categories`, `GET /admin/categories/{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | ADMIN |
| Products | `GET /products`, `GET /products/{id}` | Public (active only) |
| Admin products | `GET /admin/products`, `GET /admin/products/{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` | ADMIN |
| Reviews | `GET /products/{productId}/reviews` | Public |
| Reviews | `POST /products/{productId}/reviews`, `PATCH/DELETE /reviews/{id}` | Authenticated owner |
| Cart | `GET /cart`, `POST /cart/items`, `PATCH/DELETE /cart/items/{productId}`, `DELETE /cart` | Authenticated |
| Addresses | `GET/POST /addresses`, `GET/PUT/DELETE /addresses/{id}`, `PATCH /addresses/{id}/default` | Authenticated owner |
| Orders | `POST /orders`, `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel` | Authenticated owner |
| Payments | `POST/GET /orders/{orderId}/payments`, `GET /payments/{id}`, `POST /payments/{id}/simulate` | Authenticated owner |
| Admin orders | `GET /admin/orders`, `GET /admin/orders/{id}`, `PATCH /admin/orders/{id}/status`, `GET /admin/orders/{id}/payments` | ADMIN |
| Admin inventory | `GET /admin/inventory/{productId}`, `PATCH /admin/inventory/{productId}/stock`, `GET /admin/inventory/low-stock`, `GET /admin/inventory/{productId}/transactions` | ADMIN |

### HTTP semantics

- `201 Created` for resource creation (with `Location` where useful).
- `204 No Content` for logout, delete/deactivate, and cart clear.
- `400 Bad Request` for malformed requests and bean validation.
- `401 Unauthorized` for missing/invalid/revoked credentials.
- `403 Forbidden` for authenticated users lacking permission or review eligibility.
- `404 Not Found` for missing resources or resources owned by another customer.
- `409 Conflict` for duplicate data, insufficient stock, invalid state transitions, and concurrency conflicts.
- `422 Unprocessable Content` for an empty cart at checkout.
- `500 Internal Server Error` without stack traces or internals.
- `429 Too Many Requests` for login/register rate limits.

Errors share this shape:

```json
{
  "timestamp": "2026-10-27T01:40:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "INSUFFICIENT_STOCK",
  "message": "Only 1 unit(s) of Demo Product are available",
  "path": "/api/v1/orders",
  "fieldErrors": null
}
```

Validation errors add `fieldErrors: [{ "field": "quantity", "message": "must be greater than or equal to 1" }]`.

## Example requests and responses

### Create an order (idempotent)

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: 7f240b7a-1a68-4f8a-b0a5-7ab9b1d59a11" \
  -H "Content-Type: application/json" \
  -d '{"addressId":"<address-uuid>"}'
```

```json
{
  "id": "f714...",
  "customerId": "f53e...",
  "status": "PENDING",
  "items": [
    {
      "productId": "cc1b...",
      "productName": "Aurora Noise-Cancelling Headphones",
      "sku": "NX-EL-001",
      "unitPrice": 149.99,
      "quantity": 1,
      "lineTotal": 149.99
    }
  ],
  "subtotal": 149.99,
  "shippingCost": 0.00,
  "discountAmount": 0.00,
  "totalAmount": 149.99,
  "currency": "USD",
  "shippingAddress": {
    "recipientName": "Maya Hassan",
    "line1": "42 Demo Street",
    "line2": "Apartment 7",
    "city": "Cairo",
    "state": "Cairo",
    "postalCode": "11511",
    "country": "Egypt",
    "phone": "+20 100 000 0000"
  },
  "cancelledAt": null,
  "createdAt": "2026-10-27T01:42:00Z",
  "updatedAt": "2026-10-27T01:42:00Z"
}
```

Retry the same request with the same authenticated customer and key; the original order is returned and inventory is not deducted again. Use a new key for a genuinely new order. The key is scoped by customer.

### Initiate a simulated payment

```bash
curl -X POST http://localhost:8080/api/v1/orders/$ORDER_ID/payments \
  -H "Authorization: Bearer $TOKEN"
```

### Complete the simulated payment (development only)

```bash
curl -X POST http://localhost:8080/api/v1/payments/$PAYMENT_ID/simulate \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"outcome":"SUCCESS"}'
```

The simulated success moves the order from `PENDING` to `CONFIRMED`. A repeated success is idempotent and does not double-confirm or double-charge anything.

### Admin stock adjustment

```bash
curl -X PATCH http://localhost:8080/api/v1/admin/inventory/$PRODUCT_ID/stock \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"delta":-3,"reason":"Damaged units removed during stock count"}'
```

Send either `delta` or `quantity`, not both. A reason is required.

The full request/response collection is available at [docs/nexacart.postman_collection.json](docs/nexacart.postman_collection.json).

## Development accounts and seeding

Seeding runs only in the `dev` profile with `nexacart.seed.enabled=true`; it is idempotent by email/SKU. It creates:

- Administrator: `NEXACART_ADMIN_EMAIL` / `NEXACART_ADMIN_PASSWORD` (created only if the password is non-empty).
- Customers: `customer1@nexacart.local` and `customer2@nexacart.local` with `NEXACART_SEED_CUSTOMER_PASSWORD`.
- One default address for `customer1`.
- Four categories and twelve products with varying stock levels and low-stock thresholds.

With the example `.env.example` values:

| Account | Email | Password |
|---|---|---|
| Administrator | `admin@nexacart.local` | `ChangeMe_Admin_Dev_123!` |
| Customer | `customer1@nexacart.local` | `Customer123!` |
| Customer | `customer2@nexacart.local` | `Customer123!` |

These credentials are explicitly development-only. No administrator is created automatically outside the `dev` profile, and the seeder never logs passwords.

## Payment simulation limitations

- `SimulatedPaymentProvider` is **not a real payment processor**. A simulated success is only a local state transition; no money moves and no card data is accepted or stored.
- The simulator is enabled by the `dev` profile and is blocked when `prod`/`production` is active, independent of `NEXACART_PAYMENT_SIMULATOR_ENABLED`.
- There is no raw card-number or CVV storage anywhere in the schema or API.
- Duplicate completion notifications are safe because the payment row is locked, terminal states are checked, provider references are unique, and only one pending payment per order is allowed.
- Refunds are synchronous in the simulator. A real provider may need asynchronous refund callbacks, signatures, and reconciliation.
- To integrate a real provider, implement `PaymentProvider`, verify webhook signatures against the provider's official documentation, store provider event IDs for deduplication, and disable the simulator in production.
- The client-provided simulation outcome is trusted only because this is an explicitly isolated development simulator. It is never treated as production payment evidence.

## Security considerations

- BCrypt cost 12; plaintext passwords are never stored or returned.
- JWT HMAC-SHA256 signature, expiry, and issuer are validated; `jti` revocation and current account state are checked per request.
- Secret is loaded from the environment and must be at least 32 bytes after Base64 decoding.
- Stateless sessions and CSRF disabled because no cookies are used for authentication. If cookie-based auth is added, CSRF protection must be enabled.
- CORS uses an explicit origin list and allows only the headers required by the API.
- Method and URL authorization protect administrative endpoints.
- Ownership checks protect cart, addresses, orders, payments, and reviews. Cross-customer access returns 404 to avoid resource enumeration.
- DTOs prevent mass assignment; public registration cannot assign a role; requests never contain trusted prices or totals.
- Bean Validation on all request bodies; DB constraints enforce critical invariants.
- JPA parameter binding and specification predicates avoid string-concatenated SQL.
- Central exception handling returns stable error codes without stack traces.
- Actuator exposes only health and info; health details are hidden.
- Sensitive login/register endpoints have a single-instance fixed-window IP rate limit.
- Logs do not include passwords or bearer tokens.
- Database `clean` is disabled and Flyway migrations own schema changes.

### Known security/operational limitations

- The rate limiter is in-memory and per-instance; a distributed deployment needs Redis or an API gateway.
- Access tokens cannot be un-issued except through the denylist (logout), account disablement, or role changes. There are no refresh tokens or token families.
- Every authenticated request performs database checks for revocation and current role. This is a deliberate security-over-latency trade-off.
- The payment simulator is development-only and does not represent a production payment integration.
- Order creation reserves stock immediately by deducting it. There is no scheduled expiration for unpaid orders yet.
- Admin status changes are trusted administrative operations; an admin may confirm an unpaid order deliberately.
- Swagger/OpenAPI is public in dev. Disable it in prod (default in `application-prod.yml`).

## Known limitations

- No refresh tokens, email verification, password reset, or multi-factor authentication.
- No discount-code engine; `discountAmount` is a persisted field currently always `0.00`.
- No order-expiration scheduler for abandoned `PENDING` orders.
- No email/SMS notifications, outbox, or message broker.
- No product variants/options or per-variant inventory.
- No real payment provider integration.
- No admin review moderation.
- Shipping fee/free-shipping threshold are global configuration, not per-region rules.
- Search is SQL `LIKE` over indexed trigram expressions rather than a dedicated search engine.
- No automated CI workflow is included.

## Potential future improvements

- Real payment-provider adapter with signed webhooks, provider event deduplication, and asynchronous refunds.
- Expirable stock reservations for unpaid orders (scheduled job plus reservation table or `expires_at`).
- Refresh-token rotation and token-family revocation.
- Redis-backed distributed rate limiting/caching.
- Transactional outbox plus email notifications for order events.
- Discount/promotion engine with usage limits.
- Product variants and warehouse-level inventory.
- Full-text search/Elasticsearch if catalog size requires it.
- OpenTelemetry metrics/tracing and structured JSON logs.
- CI workflow running `mvn verify`, Docker build, and dependency/security scanning.

## Documentation index

- [Architecture and ER diagram](docs/ARCHITECTURE.md)
- [Development guide and design decisions](docs/DEVELOPMENT.md)
- [Six-point engineering handoff](docs/HANDOFF.md)
- [Postman collection](docs/nexacart.postman_collection.json)
