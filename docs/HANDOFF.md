# Engineering Handoff

> Point-in-time delivery and verification record. Canonical guidance lives in the [documentation index](README.md), [architecture](ARCHITECTURE.md), [project structure](PROJECT_STRUCTURE.md), [development guide](DEVELOPMENT.md), and [operations guide](OPERATIONS.md). This file is historical context, not a substitute for those documents.

## Delivered scope

NexaCart is a modular Spring Boot monolith covering:

- Customer registration, login, JWT revocation, profile management, ADMIN/CUSTOMER authorization, admin account controls, and auth rate limiting.
- Category and product administration, SKU/slug rules, catalog visibility, search, filters, sorting, and pagination.
- One server-side cart per customer, server-calculated totals, stock validation, and inactive-product handling.
- Inventory initialization, administrative adjustments, low-stock reporting, transaction history, pessimistic locking, and cancellation restocks.
- Address CRUD with ownership checks, a single-default-address invariant, and shipping snapshots on orders.
- Transactional checkout with database-backed idempotency, immutable item/price snapshots, validated status transitions, cancellation rules, and inventory restoration.
- Payment-provider abstraction, development-only simulated payments, idempotent completion, refunds, and duplicate-notification safety.
- Verified-purchase reviews, rating summaries, and ownership-protected updates/deletes.
- Flyway schema management, Hibernate validation, centralized errors, OpenAPI/Swagger, Actuator, Docker/Compose, seed data, a Postman collection, and a CI workflow.

## Verification snapshot

For this revision:

- `mvn clean verify` passes with **59 tests, 0 failures, 0 errors, 0 skipped** against PostgreSQL 17 via Testcontainers.
- Flyway migration and Hibernate schema validation are exercised by the integration tests.
- Concurrency tests cover last-unit oversell prevention and concurrent same-idempotency-key checkout.
- The multi-stage Java 21 container image builds from the repository root.
- `docker compose config` validates the local PostgreSQL/application stack.
- CI (`.github/workflows/ci.yml`) runs Maven verification and the container-image build on pushes to `main` and on pull requests.

Full-stack runtime behavior (login, catalog, order, payment, review) is exercised by the integration suite. A full Compose startup should still be smoke-tested when changing Docker or environment configuration.

## Key decisions

- **Pessimistic inventory locking** was chosen over optimistic retries because checkout must atomically validate and mutate multiple stock rows and record exact previous/new quantities for the audit ledger.
- **Stock is deducted at order creation**, preventing overselling while payment is pending. Abandoned pending orders hold stock until cancelled; an expiration job is future work.
- **The cart row is locked before the idempotency re-check and inventory locks**, serializing duplicate retries and cart mutations.
- **Checkout loads cart items without fetching inventory entities before the lock query**, avoiding stale Hibernate version conflicts.
- **JWT role claims are not used for authorization.** Authorities are resolved from the current database account, closing the role-change window without issuing new tokens.
- **Order `discountAmount` is persisted but currently always zero** because no promotion rules exist.
- **Payments are simulated by design.** Responses identify the provider as simulated, no card data is accepted, and the endpoint is blocked under `prod`/`production`.
- **PostgreSQL is the only test database.** H2 was intentionally avoided because it cannot validate PostgreSQL locking, partial indexes, or constraint behavior.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full rationale and trade-offs.

## Limitations and next steps

- No real payment provider: add signed raw-body webhooks, provider event deduplication, asynchronous refunds, and reconciliation.
- No refresh tokens, email verification, password reset, MFA, or email/SMS notifications.
- No scheduled expiration for unpaid pending orders; stock remains reserved until cancellation.
- Rate limiting is in-memory and per-instance; distributed deployments need Redis or an API gateway.
- No discount/promotion engine, product variants, per-warehouse stock, or admin review moderation.
- Search uses PostgreSQL trigram-indexed `LIKE`, not a dedicated search engine.
- The production profile expects external database credentials and JWT secret and has not been exercised against a real payment provider or hosting platform.

See [OPERATIONS.md](OPERATIONS.md#known-limitations) for the canonical limitations list and [OPERATIONS.md#potential-future-improvements](OPERATIONS.md#potential-future-improvements) for suggested work.
