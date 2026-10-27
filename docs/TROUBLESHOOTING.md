# Troubleshooting

Symptom-oriented guidance for local development and operations. For the canonical behavior, see [Configuration and operations](OPERATIONS.md), [API guide](API.md), and [Architecture](ARCHITECTURE.md).

## Startup and configuration

### The application exits because the JWT secret is missing or invalid

`JwtService` refuses to start the context unless `NEXACART_JWT_SECRET` is present. The exact startup messages are:

- `NEXACART_JWT_SECRET must be set to a Base64-encoded secret of at least 32 bytes`
- `NEXACART_JWT_SECRET must be valid Base64`
- `NEXACART_JWT_SECRET must decode to at least 32 bytes`

Generate a valid value and export it before starting Maven or a JAR:

```bash
export NEXACART_JWT_SECRET="$(openssl rand -base64 48)"
```

For Compose, set the same variable in `.env` (the quick start in the [README](../README.md#quick-start) shows the command). A missing value in `.env` does not fall back to an insecure default.

### The app container starts but is unhealthy

```bash
docker compose ps
docker compose logs postgres
docker compose logs app
```

Common causes:

- PostgreSQL is still starting. Wait for the `postgres` health check to pass; the app waits for it.
- `DB_URL`, `DB_USER`, or `DB_PASSWORD` do not match the PostgreSQL service.
- The JWT secret is missing or invalid.
- The host port mapped by `APP_PORT` is already in use.

The health probe is `GET /actuator/health`. Only the status is exposed; details are hidden by configuration.

### Port 5432 or 8080 is already in use

Change the host-side ports in `.env`:

```bash
DB_PORT=5433
APP_PORT=8081
```

The application always listens on container port `8080`; `APP_PORT` only changes the Compose host mapping. If you run the JAR directly, set `SERVER_PORT` instead.

### Flyway reports a checksum or validation failure

Applied migrations are immutable. Do not edit `V1__initial_schema.sql` or any other migration that has already run. Add a new `V<n>__description.sql` migration instead.

For a disposable local database only, reset the volume:

```bash
docker compose down -v
docker compose up -d postgres
```

This deletes local data. Never use it against shared or production data.

### Flyway cannot create `pg_trgm`

The first migration runs `CREATE EXTENSION IF NOT EXISTS pg_trgm`. The PostgreSQL image used by Compose permits this. A restricted managed database may reject extension creation; a database administrator must enable `pg_trgm` before the application migrates.

### Hibernate schema validation fails

The application runs with `ddl-auto: validate`; it never changes the schema. A validation failure means the JPA entities and Flyway migrations have drifted. Compare the entity mapping with the migration, add a forward migration, and run the integration tests. Do not switch to `ddl-auto: update` to make the error disappear.

## Tests

### Testcontainers cannot find Docker

Integration tests start PostgreSQL 17 through Testcontainers. There is intentionally no H2 or embedded fallback.

```bash
docker info
mvn test
```

If `docker info` fails, start the Docker daemon. If the image is not cached and the registry is unreachable, pull it explicitly with `docker pull postgres:17-alpine`.

### Running one test or one test method

```bash
mvn -Dtest=AuthIntegrationTest test
mvn -Dtest=OrderIntegrationTest#cancellationRestoresStockExactlyOnce test
```

Integration tests still require Docker. Unit tests that do not extend `IntegrationTestBase` do not need a database, but the Maven test run as a whole may still include integration tests unless you select a class or method.

### Tests pass locally but CI fails

CI runs `mvn -B clean verify` on JDK 21. Confirm the local build uses a compatible JDK and a clean working tree:

```bash
java -version
mvn -version
mvn -B clean verify
```

The Maven build targets Java 21 via `<release>21</release>` even when a newer JDK is installed. Do not disable tests or validation to make CI pass.

## Authentication and authorization

### `401 UNAUTHORIZED` on a request that used to work

Possible causes:

- The access token expired. The default TTL is 900 seconds; there are no refresh tokens.
- The token was revoked by `POST /api/v1/auth/logout` or by an administrator disabling the account.
- The account no longer exists or is disabled.
- The `Authorization` header is missing or is not in `Bearer <token>` form.

Log in again to obtain a new token. Role changes do not require a new token because authorities are read from the database on each request, but a disabled account invalidates existing tokens immediately.

### `401 INVALID_CREDENTIALS` on login

The API intentionally returns the same message for an unknown email, a wrong password, and a disabled account. Verify the credentials and the account's `enabled` flag through the admin API.

### `403 ACCESS_DENIED` on an admin endpoint

The caller is authenticated but is not an enabled `ADMIN`. Use an administrator token created under the `dev` profile or promoted through `PATCH /api/v1/admin/users/{id}`.

An administrator cannot demote or disable their own account (`SELF_ADMIN_CHANGE_NOT_ALLOWED`), and the last enabled administrator cannot be removed (`LAST_ADMIN`).

### `429 RATE_LIMIT_EXCEEDED`

Login and registration use a fixed-window, in-memory limiter keyed by remote address and request path. Wait for the window or adjust:

```bash
export NEXACART_RATE_LIMIT_MAX_ATTEMPTS=20
export NEXACART_RATE_LIMIT_WINDOW_SECONDS=60
```

The response includes `Retry-After`. The limiter is per instance; it does not coordinate across replicas. Integration tests disable it through the `test` profile.

### The development administrator was not created

The dev seeder creates the administrator only when `NEXACART_ADMIN_PASSWORD` is non-empty. The seeder logs a warning and skips it otherwise. Set the variable and restart the `dev` profile application. The same restriction applies to seeded customers through `NEXACART_SEED_CUSTOMER_PASSWORD`.

## Orders, inventory, and payments

### Checkout returns `422 CART_EMPTY`

The customer has no cart items, or all items were removed. Add an item with `POST /api/v1/cart/items` before creating the order. Checkout also requires an owned `addressId` in the request body.

### Checkout returns `409 INSUFFICIENT_STOCK` or `409 PRODUCT_UNAVAILABLE`

Stock is validated at checkout, not only when the item is added to the cart. Another order may have consumed the remaining stock, or the product/category may have been deactivated. Refresh the cart, reduce the quantity, or have an administrator replenish stock. The failed checkout leaves stock and orders unchanged.

### An order cannot be cancelled

Customer cancellation is limited to `PENDING` and `CONFIRMED`. Administrators can additionally cancel `PROCESSING`. Orders that are `SHIPPED`, `DELIVERED`, or already `CANCELLED` do not accept cancellation; repeated customer cancellation of an already `CANCELLED` order returns the existing order.

### Payment endpoints return `503 PAYMENT_PROVIDER_UNAVAILABLE`

No real payment provider is implemented. Both payment initiation (`POST /api/v1/orders/{orderId}/payments`) and simulation (`POST /api/v1/payments/{id}/simulate`) require the development simulator:

```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

or set `NEXACART_PAYMENT_SIMULATOR_ENABLED=true` outside `prod`/`production`. The simulator is always blocked when `prod` or `production` is active, regardless of that setting. Reading existing payment records is not gated by the simulator.

### Payment completion returns `409`

- `ORDER_NOT_PAYABLE`: payment can only be initiated while the order is `PENDING`.
- `PAYMENT_ALREADY_FINALIZED`: the payment is already `SUCCEEDED`, `FAILED`, or `REFUNDED`. Repeating the same successful or failed outcome is idempotent; trying to change a terminal outcome is a conflict.
- `ORDER_CANCELLED`: the order was cancelled before completion.
- `UNSUPPORTED_PROVIDER`: the simulator only completes payments created by the simulated provider.

A failed payment can be retried by initiating a new payment for the same `PENDING` order. Only one `PENDING` payment per order is allowed at a time.

## API and data behavior

### `400 MALFORMED_REQUEST` when creating an order

`Idempotency-Key` is a required header with a maximum length of 100 characters. It is trimmed before use. Missing or blank headers are rejected before the service runs. Missing request bodies, invalid JSON, missing query parameters, and type mismatches also produce this code.

### `400 VALIDATION_FAILED`

Inspect `fieldErrors` in the response. Bean Validation constraints cover required fields, lengths, quantities, prices, and ratings. The top-level `message` is intentionally generic.

### `404` for a resource that exists

Customer-owned resources are scoped to the authenticated user. A cart, address, order, payment, or review belonging to another customer returns `404`, not `403`, to avoid enumeration. Public catalog reads also return `404` for inactive products and for products in inactive categories.

### `LazyInitializationException` after a code change

`spring.jpa.open-in-view` is `false`. JPA entities are not serialized and must be read inside the service transaction that owns them. Fetch required associations in the repository (entity graph or join fetch) or map to a response DTO inside the service. Do not re-enable open-in-view as a workaround.

### `409 CONCURRENT_MODIFICATION` or `409 DATA_CONFLICT`

The database detected an optimistic or pessimistic lock conflict, or a unique constraint was violated. For checkout, the service already resolves the idempotency-key race and returns the winning order. Other write operations may need to be retried by the caller. Do not bypass constraints in the schema.

### A product or category disappears from the public catalog

Deletion is soft:

- `DELETE /api/v1/admin/products/{id}` sets the product status to `INACTIVE`.
- `DELETE /api/v1/admin/categories/{id}` sets the category `active` flag to `false`.

Public product reads and listings only include `ACTIVE` products in active categories. Administrators can still read them through the admin endpoints. Products in an inactive category stop being purchasable, and an `ACTIVE` product cannot be assigned to an inactive category.

## Local reset and diagnostics

```bash
docker compose ps
docker compose logs --tail=200 app
docker compose logs --tail=200 postgres
docker compose exec postgres psql -U nexacart -d nexacart
```

If `POSTGRES_USER` or `POSTGRES_DB` were changed in `.env`, use those values in the `psql` command instead of the defaults.

To reset the local database and rerun the dev seed:

```bash
docker compose down -v
docker compose up --build
```

This is destructive. Do not use it against data you need to keep. There is no automated backup, restore, or migration-rollback procedure in the repository; use PostgreSQL tooling and forward-only Flyway migrations.
