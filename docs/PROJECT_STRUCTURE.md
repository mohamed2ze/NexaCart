# Project Structure

This document is the authoritative reference for where code, tests, configuration, and documentation belong. It describes the repository as it exists; update it whenever a structural convention changes.

## High-level tree

```text
NexaCart/
├── .github/workflows/ci.yml       GitHub Actions verification and image build
├── docs/                          Project documentation and API collection
│   ├── README.md                  Documentation index
│   ├── ARCHITECTURE.md            System design and invariants
│   ├── PROJECT_STRUCTURE.md       This document
│   ├── DEVELOPMENT.md             Contribution and testing guide
│   ├── API.md                     HTTP contract and examples
│   ├── OPERATIONS.md              Configuration, deployment, and limitations
│   ├── HANDOFF.md                 Point-in-time delivery record
│   └── nexacart.postman_collection.json
├── src/
│   ├── main/
│   │   ├── java/com/nexacart/
│   │   │   ├── NexaCartApplication.java
│   │   │   ├── address/           Address CRUD and default-address rules
│   │   │   ├── auth/              Registration, login, logout/revocation
│   │   │   ├── cart/              Per-customer cart and totals
│   │   │   ├── category/          Category lifecycle and views
│   │   │   ├── common/            Shared base entity, errors, pricing, responses, utilities
│   │   │   ├── config/            Properties, security wiring, OpenAPI, dev seeding
│   │   │   ├── inventory/         Stock balances, ledger, low-stock reporting
│   │   │   ├── order/             Checkout, snapshots, state machine, cancellation
│   │   │   ├── payment/           Provider abstraction and simulated payments
│   │   │   ├── product/           Catalog, search, SKU/slug rules
│   │   │   ├── review/            Verified-purchase reviews and rating summaries
│   │   │   ├── security/          JWT, validation, rate limiting, security errors
│   │   │   └── user/              Accounts, roles, profiles, admin management
│   │   └── resources/
│   │       ├── application.yml    Shared configuration
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/migration/      Flyway SQL migrations
│   └── test/
│       ├── java/com/nexacart/     Unit tests colocated with their domain
│       │   └── support/           Shared integration base and context test
│       └── resources/application-test.yml
├── .dockerignore
├── .env.example
├── .gitignore
├── AGENTS.md                      Repository map for AI coding agents
├── compose.yaml
├── Dockerfile
├── pom.xml
└── README.md
```

## Directory responsibilities

### Business domains

Each domain package follows the same layer structure:

```text
<domain>/
├── controller/   HTTP mapping, status codes, request validation entry points
├── dto/          Request/response records and mapping factories
├── entity/       JPA persistence model; never returned from controllers
├── repository/   Spring Data interfaces, queries, and specifications
└── service/      Business rules and transaction boundaries
```

| Package | Owns |
|---|---|
| `address` | Customer address CRUD and the single-default-address invariant. |
| `auth` | Registration, login, JWT issuing, logout/revocation, and expired-token cleanup. |
| `cart` | Cart ownership, item quantities, server-side totals, and checkout readiness. |
| `category` | Category lifecycle, slugs, and public/admin category views. |
| `inventory` | Stock balances, pessimistic locking helpers, adjustments, low-stock views, and the stock ledger. |
| `order` | Cart checkout, idempotency, snapshots, order state transitions, cancellation, and admin order views. |
| `payment` | Payment records, provider abstraction, simulated lifecycle, completion, and refunds. |
| `product` | Catalog administration, visibility rules, search/filter/sort/page, and product inventory initialization. |
| `review` | Verified-purchase eligibility, review CRUD, and rating summaries. |
| `user` | Accounts, roles, profiles, and administrator account management. |

### Cross-cutting packages

| Package | Responsibility |
|---|---|
| `common` | Domain-neutral building blocks: `BaseEntity`, `ApiException`/`GlobalExceptionHandler`, `ApiError`/`PageResponse`, `PricingService`, and `SlugUtil`. `common.pricing` is shared by cart and order rather than belonging to either. |
| `config` | `NexaCartProperties`, `SecurityConfig`, `OpenApiConfig`, and the dev-only `DevDataSeeder`. |
| `security` | `JwtService`, `JwtTokenValidator`, `AuthRateLimitFilter`, and `SecurityErrorHandler`. |

### Tests

- Unit tests live in the package of the class under test. Examples: `common/pricing/PricingServiceTest` and `order/service/OrderStateMachineTest`.
- Integration tests live in their domain package and extend `com.nexacart.support.IntegrationTestBase`, which starts one PostgreSQL Testcontainer and truncates tables between tests.
- `support/ApplicationContextIntegrationTest` verifies context startup, Flyway history, and schema validation.
- There is no H2 test profile; Docker is required for integration tests.

### Documentation and tooling

| Path | Purpose |
|---|---|
| `docs/README.md` | Documentation index and source-of-truth map. |
| `docs/ARCHITECTURE.md` | Component boundaries, data model, request lifecycle, transactions, security, and trade-offs. |
| `docs/DEVELOPMENT.md` | Local workflow, feature checklist, conventions, testing, and pitfalls. |
| `docs/API.md` | Authentication, endpoint table, status semantics, errors, and examples. |
| `docs/OPERATIONS.md` | Environment variables, profiles, database operations, deployment, and limitations. |
| `docs/HANDOFF.md` | Historical delivery/verification snapshot. Not canonical guidance. |
| `docs/nexacart.postman_collection.json` | Manually maintained executable API requests. |
| `.github/workflows/ci.yml` | CI: `mvn clean verify` plus a container-image build. |
| `compose.yaml` | Local PostgreSQL and application stack. |
| `Dockerfile` | Multi-stage Java 21 build and runtime image. |

There is deliberately no `scripts/` directory. Build, test, database, and container operations use Maven, Flyway, and Docker Compose directly.

## Important entry points

- Application startup: `src/main/java/com/nexacart/NexaCartApplication.java` (`main`).
- HTTP contract: `src/main/java/com/nexacart/*/controller/`.
- Schema: `src/main/resources/db/migration/` (Flyway owns the schema; Hibernate only validates).
- Runtime configuration: `src/main/resources/application*.yml`, overridden by environment variables.
- Local stack: `compose.yaml`; container build: `Dockerfile`.
- Validation: `pom.xml`, `src/test/`, and `.github/workflows/ci.yml`.

## Where to add new functionality

| Change | Location |
|---|---|
| New business capability | New package under `src/main/java/com/nexacart/<domain>/` with `controller`, `dto`, `entity`, `repository`, and `service` as needed. |
| New endpoint on an existing capability | Existing domain's `controller`, `service`, and `dto` packages. |
| New persistence field or table | JPA entity plus a **new** Flyway migration (`V2__...sql`, `V3__...sql`). Never edit an applied migration. |
| Shared, domain-neutral helper | `common/` only when multiple domains need it and it does not encode domain rules. Prefer keeping domain rules in the owning domain. |
| Runtime setting | `NexaCartProperties`, `application.yml`, the relevant profile, `.env.example`, `compose.yaml` when Compose should forward it, and `docs/OPERATIONS.md`. |
| Unit test | The package of the class under test. |
| Integration test | The relevant domain test package, extending `IntegrationTestBase`. |
| Public API documentation | `docs/API.md` and the controller annotations. |
| Architecture or setup change | The relevant document in `docs/` and `docs/README.md`. |
| CI or container behavior | `.github/workflows/ci.yml`, `Dockerfile`, and `compose.yaml`. |

## Generated and maintained files

- `target/` and packaged JARs are generated by Maven and ignored by Git. Do not edit or commit them.
- `src/main/resources/db/migration/` is maintained by developers; applied migrations are immutable. The database schema is generated by Flyway at startup.
- `docs/nexacart.postman_collection.json` is maintained manually; regenerate or update it when endpoints change.
- There is no annotation-processor source generation in this project beyond Lombok-generated accessors.
- Never commit `.env`, secrets, IDE state, logs, or build output.

## Naming and organization conventions

- Java packages are lower-case and organized by business domain, then by layer.
- Classes use the suffixes `Controller`, `Service`, `Repository`, `Request`, `Response`, `Properties`, etc.
- Tests use `*Test` for unit tests and `*IntegrationTest` for Spring/Testcontainers tests.
- REST paths use plural nouns under `/api/v1`; administrative resources live under `/api/v1/admin`.
- JSON uses lower camel case; database identifiers use snake case; enums use upper snake case.
- Money uses `BigDecimal` with scale 2; persistence uses `Instant`/`TIMESTAMPTZ` in UTC.
- Flyway files use `V<n>__snake_case_description.sql`.
- Documentation filenames use upper-case names matching the existing `docs/` convention.

## Dependency direction and boundaries

```text
controller -> service -> repository -> PostgreSQL
      |           |
      +-> dto     +-> entities and other domain services/repositories
```

- Controllers contain HTTP concerns only; services own business rules and transactions; repositories handle data access.
- DTO records define the external contract. JPA entities are never serialized or accepted directly.
- Cross-domain collaboration uses explicit service/repository calls. Keep dependencies directional and avoid introducing cycles.
- `common` and `security` must not accumulate domain-specific business rules.
- Ownership checks belong in services, and cross-customer access returns 404 to avoid enumeration.
- Inventory changes must follow the locking and ledger rules in [DEVELOPMENT.md](DEVELOPMENT.md#5-inventory-checklist).
- See [ARCHITECTURE.md](ARCHITECTURE.md) for the full module map and invariants.

## Exceptions to the general layout

- `BaseEntity` sits directly in `common`, not `common/entity`, because it is the shared persistence base for all domains.
- `common/pricing/PricingService` is shared by cart and order; it is not owned by either domain.
- The `support` test package holds only shared test infrastructure, not product code.
