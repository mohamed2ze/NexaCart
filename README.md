# NexaCart

NexaCart is a production-minded e-commerce REST API built with Java and Spring Boot. It covers customer accounts, JWT authentication, a product catalog, cart, inventory control, addresses, transactional order processing, simulated payments, and verified-purchase reviews.

The project is deliberately a **modular monolith**. It is easy to run locally, but the important business rules are implemented for real: server-side pricing, row-level inventory locking, database-backed order idempotency, immutable order snapshots, validated status transitions, and ownership checks on customer resources.

## What it includes

- **Accounts and security:** registration, login, server-side logout with a JWT denylist, current-role enforcement, profile management, admin user controls, and login/registration rate limiting.
- **Catalog:** category and product administration, SKU/slug rules, public visibility rules, filtering, search, sorting, and pagination.
- **Cart and inventory:** one server-side cart per customer, server-calculated totals, stock validation, administrative stock adjustments, low-stock reporting, and an immutable inventory ledger.
- **Orders and addresses:** idempotent checkout, product/price/shipping snapshots, validated status transitions, cancellation with exactly-once stock restoration, and per-customer address CRUD.
- **Payments and reviews:** a provider abstraction with a development-only simulator, idempotent payment completion/refunds, and verified-purchase reviews with rating summaries.
- **Operations:** Flyway-managed PostgreSQL schema, Hibernate schema validation, Actuator health, OpenAPI/Swagger, Docker/Compose, reproducible dev seed data, and a CI workflow.

## Technology

| Technology | Version / notes |
|---|---|
| Java | 21 target bytecode |
| Maven | 3.9+ |
| Spring Boot | 4.1.2 |
| Spring Framework | 7.0.x |
| Spring Security | 7.1.x, Nimbus JOSE JWT support |
| Spring Data JPA / Hibernate | 7.4.13.Final |
| PostgreSQL | 17 (`postgres:17-alpine`) |
| Flyway | 12.4.0 |
| springdoc-openapi | 3.2.0 |
| Testcontainers | 2.0.5 |
| JUnit / Mockito | JUnit 6.0.x / Mockito 5.23.x |
| Lombok | 1.18.48 (entities/configuration only) |
| Docker / Compose | Docker Engine with Compose v2+; local verification used Docker 29.x / Compose v5.x |

Versions are managed by the Spring Boot 4.1.2 BOM except springdoc-openapi, which is pinned in `pom.xml`. Maven dependency versions in this table were verified with `mvn dependency:list`.

## Prerequisites

- JDK 21 or newer (the build targets Java 21).
- Maven 3.9+.
- Docker Engine with a running daemon (PostgreSQL, Testcontainers, and containerized runs).
- Docker Compose v2+.
- `openssl` for generating a local JWT secret.

## Quick start

### Option A: Full Docker Compose stack

```bash
cp .env.example .env
sed -i "s|^NEXACART_JWT_SECRET=.*|NEXACART_JWT_SECRET=$(openssl rand -base64 48)|" .env
docker compose up --build
```

The app waits for a healthy PostgreSQL instance and starts at <http://localhost:8080>. Stop it with `docker compose down`; use `docker compose down -v` to also delete the local database volume.

### Option B: Maven with a local PostgreSQL container

```bash
docker compose up -d postgres
export NEXACART_JWT_SECRET="$(openssl rand -base64 48)"
export NEXACART_ADMIN_PASSWORD="ChangeMe_Admin_Dev_123!"
export NEXACART_SEED_CUSTOMER_PASSWORD="Customer123!"
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

The `dev` profile enables the payment simulator and idempotent sample data. The application refuses to start without a valid `NEXACART_JWT_SECRET` (Base64, at least 32 decoded bytes). If startup or container health fails, see [Troubleshooting](docs/TROUBLESHOOTING.md).

### Seeded development accounts

| Account | Email | Password |
|---|---|---|
| Administrator | `admin@nexacart.local` | `ChangeMe_Admin_Dev_123!` |
| Customer | `customer1@nexacart.local` | `Customer123!` |
| Customer | `customer2@nexacart.local` | `Customer123!` |

These credentials are development-only. See [Configuration and operations](docs/OPERATIONS.md) for the full environment-variable and profile reference.

Once running:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Health: <http://localhost:8080/actuator/health>

## Verification

The test suite uses PostgreSQL 17 through Testcontainers, so Docker must be running.

```bash
mvn test          # unit + integration tests
mvn clean verify  # tests, schema validation, and packaged JAR
```

The suite includes unit tests and PostgreSQL Testcontainers integration tests. There is intentionally no H2 fallback: integration tests must exercise real PostgreSQL locking, partial indexes, and constraints. CI runs `mvn clean verify` and a container-image build on every push to `main` and every pull request.

## Documentation

Start at the [documentation index](docs/README.md).

- [Architecture](docs/ARCHITECTURE.md) — components, request lifecycle, persistence, transactions, security, and trade-offs.
- [Project structure](docs/PROJECT_STRUCTURE.md) — directory responsibilities, entry points, and where new code belongs.
- [Development guide](docs/DEVELOPMENT.md) — local workflow, conventions, testing, migrations, and common pitfalls.
- [API guide](docs/API.md) — authentication, endpoint overview, HTTP semantics, and request examples.
- [Configuration and operations](docs/OPERATIONS.md) — environment variables, profiles, database setup, deployment, security posture, and limitations.
- [Troubleshooting](docs/TROUBLESHOOTING.md) — symptom-based fixes for startup, database, tests, auth, and payments.
- [Engineering handoff](docs/HANDOFF.md) — point-in-time delivery and verification snapshot.
- [Postman collection](docs/nexacart.postman_collection.json) — executable API requests.

## Project layout

```text
NexaCart/
├── .github/workflows/ci.yml     CI verification and container build
├── docs/                        Architecture, structure, API, operations
├── src/main/java/com/nexacart/  Domain packages plus common/config/security
├── src/main/resources/          Application config and Flyway migrations
├── src/test/                    Unit and Testcontainers integration tests
├── compose.yaml                 Local PostgreSQL + application stack
├── Dockerfile                   Multi-stage Java 21 image
├── .env.example                 Development environment template
└── pom.xml                      Maven build and dependency management
```

Domain code is organized by business capability, with each package owning its `controller`, `dto`, `entity`, `repository`, and `service` layers. See [Project structure](docs/PROJECT_STRUCTURE.md) for the complete conventions.

## Scope and limitations

NexaCart is a reference implementation, not a production-ready payment platform. Payments are simulated; the simulator is blocked in `prod`/`production`. There are no refresh tokens, email verification, password reset, order-expiration job, promotions engine, product variants, or real payment-provider integration. Rate limiting is in-memory and per-instance.

See [Configuration and operations](docs/OPERATIONS.md#known-limitations) for the full list.
