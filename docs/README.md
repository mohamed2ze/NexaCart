# NexaCart Documentation

This directory is the entry point for project documentation. Canonical guidance lives here and in the root [README](../README.md); avoid duplicating rules across files.

## Start here

| If you want to... | Read |
|---|---|
| Set up and run the project | [README quick start](../README.md#quick-start) |
| Understand the system design | [Architecture](ARCHITECTURE.md) |
| Find where code belongs | [Project structure](PROJECT_STRUCTURE.md) |
| Contribute code or tests | [Development guide](DEVELOPMENT.md) |
| Call the API | [API guide](API.md) |
| Configure, test, or deploy | [Configuration and operations](OPERATIONS.md) |
| Diagnose a failure | [Troubleshooting](TROUBLESHOOTING.md) |
| See the latest delivery snapshot | [Engineering handoff](HANDOFF.md) |

## Document types

- **Explanation:** [Architecture](ARCHITECTURE.md) — design, data model, transactions, security, and trade-offs.
- **Reference:** [API guide](API.md), [Configuration and operations](OPERATIONS.md), and [Project structure](PROJECT_STRUCTURE.md).
- **How-to:** [README quick start](../README.md#quick-start), [Development guide](DEVELOPMENT.md), [Configuration and operations](OPERATIONS.md), and [Troubleshooting](TROUBLESHOOTING.md).
- **Historical:** [Engineering handoff](HANDOFF.md) — a point-in-time delivery record, not canonical guidance.

## Recommended reading order

1. [README](../README.md) — purpose, quick start, and verification.
2. [Architecture](ARCHITECTURE.md) — modular-monolith boundaries, data model, transactions, and security.
3. [Project structure](PROJECT_STRUCTURE.md) — package layout, file ownership, and extension points.
4. [Development guide](DEVELOPMENT.md) — conventions, migrations, tests, and common pitfalls.

Read [API](API.md) when changing the HTTP contract and [Configuration and operations](OPERATIONS.md) when changing runtime settings or deployment behavior. Use [Troubleshooting](TROUBLESHOOTING.md) when a command or request fails.

## Documents

- [ARCHITECTURE.md](ARCHITECTURE.md) — component map, ER diagram, order/payment flows, consistency model, and trade-offs.
- [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) — directory tree, responsibilities, naming conventions, generated files, and where to add functionality.
- [DEVELOPMENT.md](DEVELOPMENT.md) — local workflow, feature checklist, transaction rules, testing guide, and build verification.
- [API.md](API.md) — authentication workflow, endpoint reference, HTTP status semantics, error codes, and examples.
- [OPERATIONS.md](OPERATIONS.md) — environment variables, profiles, database setup, logging, run modes, security posture, deployment, and limitations.
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) — symptom-based diagnosis for startup, database, tests, auth, inventory, and payments.
- [HANDOFF.md](HANDOFF.md) — point-in-time delivery and verification record; not a substitute for the guides above.
- [nexacart.postman_collection.json](nexacart.postman_collection.json) — importable request collection for the main API flows.

## Agent and contributor guidance

- [AGENTS.md](../AGENTS.md) is the concise repository map for AI coding agents.
- [DEVELOPMENT.md](DEVELOPMENT.md) is the authoritative convention guide for both humans and agents.

## Source-of-truth rules

- Architecture and invariants: [ARCHITECTURE.md](ARCHITECTURE.md).
- Layout and ownership: [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md).
- Commands and conventions: [DEVELOPMENT.md](DEVELOPMENT.md).
- Runtime configuration and limitations: [OPERATIONS.md](OPERATIONS.md).
- HTTP contract: [API.md](API.md) and the controllers under `src/main/java/com/nexacart/*/controller/`.
- Failure diagnosis: [TROUBLESHOOTING.md](TROUBLESHOOTING.md); it is symptom-oriented and defers to the documents above for canonical behavior.

When a change alters behavior, configuration, or layout, update the relevant document and this index in the same commit.
