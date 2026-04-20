<!-- Derived from ARCHITECTURE.md — keep in sync -->

# Architecture Overview

Shelf is a single-module Kotlin application organized by domain.

## Project Layout

```
shelf/
├── src/main/kotlin/io/tarantini/shelf/
���   ├── app/                 # App bootstrap, env parsing, adapters, shared HTTP helpers
│   ├── catalog/             # Book, author, series, metadata, search, OPDS
│   ├── organization/        # Library ownership and organization features
│   ├── processing/          # Import, staging, storage, EPUB/audiobook parsing
│   ├── integration/         # Hardcover, KOReader, provider adapters
│   └── user/                # Identity, auth, tokens, activity
├── src/main/sqldelight/     # SQLDelight schema and query files by domain
├── src/test/kotlin/         # Unit and integration coverage
└── ui/                      # Svelte frontend
```

## Architectural Layers

| Layer | Responsibility | Key Notes |
|-------|----------------|-----------|
| **Routing** (`routes.kt`) | HTTP boundary; maps raw JSON DTOs to API request types | For complex mutations, map API DTOs to validated domain commands before calling services |
| **Service** (`service.kt`) | Orchestrator and gatekeeper; the only layer with business logic | Coordinates repositories/deciders via `context(_: RaiseContext)` |
| **Repository** (`repository.kt`) | Mutation port interface for service-layer testability | Thin interface over persistence; implementations delegate to `*Queries` extension functions |
| **Persistence** (`persistence.kt` / `.sq`) | SQLDelight extension functions; strictly typed entities and transactions | Save/load aggregates as single unit |

## Per-Domain File Conventions

| File | Purpose |
|------|---------|
| `primitives.kt` | Value classes and validated domain primitives |
| `api.kt` | External-facing request/response DTOs and request-to-command mappers |
| `commands.kt` | Validated command types and value objects |
| `decider.kt` | Pure decision functions: (snapshot, command) -> decision/mutation |
| `error.kt` | Domain-specific `AppError` variants |
| `models.kt` | Roots, records, aggregates, summaries |
| `normalization.kt` | Canonicalization helpers (optional) |
| `service.kt` | Orchestration, policy, and business rules |
| `persistence.kt` | Typed SQLDelight access and aggregate persistence |
| `routes.kt` | HTTP boundary only |

## Architectural Invariants

- **Service owns business logic.** Routes extract input and identity; services enforce policy and orchestration.
- **Asset resolution belongs in services.** Route handlers do not decide which cover/edition/file variant is canonical.
- **Persistence owns typed storage.** Persistence helpers contain no authorization or workflow logic.
- **Access policy is named and explicit.** Shared catalog reads/writes use dedicated policy helpers; user-owned resources enforce ownership with `requireOwnership(...)`.
- **Trust boundaries validate first.** HTTP input, uploads, filesystem metadata, and external provider data must be validated before entering the domain.
- **Storage paths are internal.** Raw storage layout is never exposed as a public interface; media is served through explicit endpoints.
- **Identity Pattern.** Use `Identity<S, ID>` with `Unsaved` and `Persisted` states.
- **Weak association across roots.** Relate roots by IDs or saved roots, not embedded aggregates.
- **Optimized reads.** Summaries for lists, aggregates for detail views.
