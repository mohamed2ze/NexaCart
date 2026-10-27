# Configuration and Operations

This document covers runtime configuration, profiles, local operations, deployment expectations, and known limitations. For build/test conventions, see [DEVELOPMENT.md](DEVELOPMENT.md).

## Profiles

| Profile | Purpose |
|---|---|
| default (no profile) | Production-like safe defaults: no dev seeding, no payment simulator. A valid JWT secret and database are still required. OpenAPI is enabled unless the production profile is active. |
| `dev` | Enables idempotent sample data and the payment simulator. Used by `compose.yaml`. |
| `test` | Test-only JWT secret and fee configuration, used with Testcontainers. Rate limiting is disabled for deterministic tests. |
| `prod` / `production` | Disables seeding, disables the payment simulator, and disables OpenAPI by default. |

`application-prod.yml` is a deployment baseline, not a complete production platform. Supply real database credentials and a JWT secret from the environment, configure CORS origins, and keep OpenAPI disabled unless it is intentionally exposed.

## Environment variables

Copy `.env.example` to `.env` when using Docker Compose. Never commit `.env` or real credentials.

```bash
cp .env.example .env
sed -i "s|^NEXACART_JWT_SECRET=.*|NEXACART_JWT_SECRET=$(openssl rand -base64 48)|" .env
```

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `NEXACART_JWT_SECRET` | Yes | none | Base64-encoded HMAC key, at least 32 decoded bytes. Generate with `openssl rand -base64 48`. |
| `NEXACART_JWT_ISSUER` | No | `nexacart` | JWT issuer and issuer validation. |
| `NEXACART_JWT_TTL_SECONDS` | No | `900` | Access-token lifetime in seconds. |
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/nexacart` | JDBC URL. Compose sets the `postgres` service host. |
| `DB_USER` | No | `nexacart` | Database user. |
| `DB_PASSWORD` | No | `nexacart_dev_password` | Database password. Use a real secret outside local development. |
| `DB_POOL_SIZE` | No | `10` | Hikari maximum pool size. |
| `SERVER_PORT` | No | `8080` | Application HTTP port. Compose maps host `APP_PORT` to container port 8080. |
| `NEXACART_CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000,http://localhost:5173` | Comma-separated explicit frontend origins. |
| `NEXACART_SHIPPING_FEE` | No | `5.99` | Flat shipping fee. |
| `NEXACART_FREE_SHIPPING_THRESHOLD` | No | `100.00` | Free-shipping subtotal threshold. |
| `NEXACART_CURRENCY` | No | `USD` | ISO currency code stored on orders/payments. |
| `NEXACART_PAYMENT_SIMULATOR_ENABLED` | No | `false` (`true` in `dev`) | Enables the development payment simulator. Ignored in `prod`/`production`, where it is always blocked. |
| `NEXACART_RATE_LIMIT_ENABLED` | No | `true` | Enables auth-endpoint rate limiting. |
| `NEXACART_RATE_LIMIT_MAX_ATTEMPTS` | No | `10` | Requests per IP and endpoint per window. |
| `NEXACART_RATE_LIMIT_WINDOW_SECONDS` | No | `60` | Fixed window length. |
| `NEXACART_SEED_ENABLED` | No | `false` (`true` in `dev`) | Enables the dev-only seeder. |
| `NEXACART_SEED_CUSTOMER_PASSWORD` | No | `Customer123!` in `dev` | Password for seeded customers. Development only. |
| `NEXACART_ADMIN_EMAIL` | No | `admin@nexacart.local` | Development administrator email. |
| `NEXACART_ADMIN_PASSWORD` | No | empty | If set in `dev`, creates the administrator. Never commit a real admin password. |
| `NEXACART_OPENAPI_ENABLED` | No | `false` in `prod` | Enables Swagger/OpenAPI in the production profile. |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_PORT`, `APP_PORT` | Compose only | dev values | Compose database and port configuration. |

The application refuses to start without a valid JWT secret. Compose forwards the JWT issuer, TTL, CORS, order, payment, rate-limit, and seed settings shown in `.env.example`.

## Database setup and migrations

Flyway runs automatically at startup from `src/main/resources/db/migration`. The initial migration creates the normalized schema, constraints, partial unique indexes, and search indexes, and enables the PostgreSQL `pg_trgm` extension.

Hibernate is configured with `ddl-auto: validate`; it never modifies the schema. Flyway `clean` is disabled.

Start only PostgreSQL with Compose:

```bash
docker compose up -d postgres
docker compose ps
```

The named volume `nexacart_pgdata` persists data across restarts. To reset local data intentionally:

```bash
docker compose down -v
```

To inspect the database:

```bash
docker compose exec postgres psql -U nexacart -d nexacart
```

## Running the application

### Option A: Full Docker Compose stack

```bash
cp .env.example .env
sed -i "s|^NEXACART_JWT_SECRET=.*|NEXACART_JWT_SECRET=$(openssl rand -base64 48)|" .env
docker compose up --build
```

The app container waits for a healthy PostgreSQL instance. Both services have health checks. Stop the stack with `docker compose down`; add `-v` to remove the database volume.

### Option B: Maven with a local PostgreSQL container

```bash
docker compose up -d postgres
export NEXACART_JWT_SECRET="$(openssl rand -base64 48)"
export NEXACART_ADMIN_PASSWORD="ChangeMe_Admin_Dev_123!"
export NEXACART_SEED_CUSTOMER_PASSWORD="Customer123!"
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

### Option C: Packaged JAR

```bash
mvn -DskipTests package
export NEXACART_JWT_SECRET="$(openssl rand -base64 48)"
java -jar target/nexacart-1.0.0.jar
```

Without the `dev` profile, seeding and the payment simulator remain disabled.

## Development accounts and seed data

Seeding runs only in the `dev` profile with `nexacart.seed.enabled=true`; it is idempotent by email and SKU. It creates:

- An administrator from `NEXACART_ADMIN_EMAIL` / `NEXACART_ADMIN_PASSWORD` (only if the password is non-empty).
- Customers `customer1@nexacart.local` and `customer2@nexacart.local` with `NEXACART_SEED_CUSTOMER_PASSWORD`.
- One default address for `customer1`.
- Four categories and twelve products with varying stock levels and low-stock thresholds.

With the example `.env.example` values:

| Account | Email | Password |
|---|---|---|
| Administrator | `admin@nexacart.local` | `ChangeMe_Admin_Dev_123!` |
| Customer | `customer1@nexacart.local` | `Customer123!` |
| Customer | `customer2@nexacart.local` | `Customer123!` |

These credentials are explicitly development-only. No administrator is created automatically outside `dev`, and the seeder never logs passwords.

## Endpoints and API documentation

When running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`
- Actuator info (authenticated): `http://localhost:8080/actuator/info`

Use the **Authorize** button in Swagger UI with a token from the login endpoint. In `prod`/`production`, OpenAPI is disabled by default and can only be enabled explicitly with `NEXACART_OPENAPI_ENABLED=true`.

## Payment simulation limitations

- `SimulatedPaymentProvider` is **not a real payment processor**. A simulated success is only a local state transition; no money moves and no card data is accepted or stored.
- The simulator is enabled by the `dev` profile and blocked when `prod`/`production` is active, regardless of `NEXACART_PAYMENT_SIMULATOR_ENABLED`.
- There is no raw card-number or CVV storage anywhere in the schema or API.
- Duplicate completion notifications are safe because the payment row is locked, terminal states are checked, provider references are unique, and only one pending payment per order is allowed.
- Refunds are synchronous in the simulator. A real provider may require asynchronous callbacks, signatures, and reconciliation.
- To integrate a real provider, implement `PaymentProvider`, verify webhook signatures against the provider's official documentation, store provider event IDs for deduplication, and disable the simulator in production.
- The client-provided simulation outcome is trusted only because this is an explicitly isolated development simulator. It is never treated as production payment evidence.

## Security posture

- BCrypt cost 12; plaintext passwords are never stored or returned.
- JWT HMAC-SHA256 signature, expiry, and issuer are validated; `jti` revocation and current account state are checked per request.
- The JWT secret is loaded from the environment and must be at least 32 bytes after Base64 decoding.
- Sessions are stateless and CSRF is disabled because no cookies are used for authentication. If cookie-based authentication is added, CSRF protection must be enabled.
- CORS uses an explicit origin list and allows only the headers required by the API.
- Method and URL authorization protect administrative endpoints.
- Ownership checks protect cart, addresses, orders, payments, and reviews. Cross-customer access returns 404 to avoid resource enumeration.
- DTOs prevent mass assignment; public registration cannot assign a role; requests never contain trusted prices or totals.
- Bean Validation covers request bodies; database constraints enforce critical invariants.
- JPA parameter binding and specification predicates avoid string-concatenated SQL.
- Central exception handling returns stable error codes without stack traces.
- Actuator exposes only health and info; health details are hidden.
- Login and registration have a single-instance fixed-window IP rate limit.
- Logs do not include passwords or bearer tokens.
- Database `clean` is disabled and Flyway migrations own schema changes.

### Security and operational limitations

- The rate limiter is in-memory and per-instance; a distributed deployment needs Redis or an API gateway.
- Access tokens cannot be un-issued except through the denylist (logout), account disablement, or role changes. There are no refresh tokens or token families.
- Every authenticated request performs database checks for revocation and current role. This is a deliberate security-over-latency trade-off.
- The payment simulator is development-only and does not represent a production payment integration.
- Order creation reserves stock immediately by deducting it. There is no scheduled expiration for unpaid orders yet.
- Admin status changes are trusted administrative operations; an admin may confirm an unpaid order deliberately.
- Swagger/OpenAPI is public in `dev`. Disable it in `prod` (the default in `application-prod.yml`).
- The project does not include TLS termination, a reverse proxy, secrets management, or a hardened production deployment manifest; those belong to the hosting platform.

## Known limitations

- No refresh tokens, email verification, password reset, or multi-factor authentication.
- No discount-code engine; `discountAmount` is a persisted field currently always `0.00`.
- No order-expiration scheduler for abandoned `PENDING` orders.
- No email/SMS notifications, outbox, or message broker.
- No product variants/options or per-variant inventory.
- No real payment-provider integration.
- No admin review moderation.
- Shipping fee and free-shipping threshold are global configuration, not per-region rules.
- Search is SQL `LIKE` over indexed trigram expressions rather than a dedicated search engine.
- Rate limiting and other in-memory state do not coordinate across instances.

## Potential future improvements

- Real payment-provider adapter with signed webhooks, provider event deduplication, and asynchronous refunds.
- Expirable stock reservations for unpaid orders (scheduled job plus reservation table or `expires_at`).
- Refresh-token rotation and token-family revocation.
- Redis-backed distributed rate limiting/caching.
- Transactional outbox plus email notifications for order events.
- Discount/promotion engine with usage limits.
- Product variants and warehouse-level inventory.
- Full-text search or a dedicated search engine if catalog size requires it.
- OpenTelemetry metrics/tracing and structured JSON logs.
- Dependency and container security scanning in CI.

## CI and deployment checks

`.github/workflows/ci.yml` runs on pushes to `main` and on pull requests:

1. `mvn -B clean verify` with JDK 21 and a PostgreSQL Testcontainer.
2. `docker build --tag nexacart:ci .`

The Dockerfile is a multi-stage Java 21 build. The Compose stack is intended for local development; the `prod` profile is the starting point for an external deployment environment.
