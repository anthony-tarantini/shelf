# ARCHITECTURE.md — Current Structure & Invariants

## Current Layout

```
shelf/
├── src/main/kotlin/io/tarantini/shelf/
│   ├── app/                 # App bootstrap, env parsing, adapters, shared HTTP helpers
│   ├── catalog/             # Book, author, series, metadata, search, OPDS
│   ├── organization/        # Library ownership and organization features
│   ├── processing/          # Import, staging, storage, EPUB/audiobook parsing
│   ├── integration/         # Hardcover, KOReader, provider adapters
│   └── user/                # Identity, auth, tokens, activity
├── src/main/sqldelight/     # SQLDelight schema and query files by domain
├── src/test/kotlin/         # Unit and integration coverage
└── ui/                      # Svelte frontend
```

## Per-Domain File Conventions

| File | Purpose |
|------|---------|
| `primitives.kt` | Value classes and validated domain primitives |
| `api.kt` | External-facing request/response DTOs |
| `models.kt` | Roots, records, aggregates, summaries |
| `error.kt` | Domain-specific `AppError` variants |
| `service.kt` | Orchestration, policy, and business rules |
| `persistence.kt` | Typed SQLDelight access and aggregate persistence |
| `routes.kt` | HTTP boundary only |

## Architectural Invariants

- **Service owns business logic:** routes extract input and identity, services enforce policy and orchestration.
- **Asset resolution belongs in services:** route handlers should not decide which cover/edition/file variant is canonical for a resource.
- **Persistence owns typed storage:** persistence helpers should not contain authorization or workflow logic.
- **Access policy is named and explicit:** shared catalog reads/writes use dedicated policy helpers, while user-owned resources enforce ownership with `requireOwnership(...)`.
- **Credential-specific auth still uses the same pattern:** JWT, OPDS basic auth, and KOReader token auth should enter through named wrappers rather than custom route-local checks.
- **Trust boundaries validate first:** HTTP input, uploads, filesystem metadata, and external provider data must be validated before entering the domain.
- **Storage paths are internal:** do not expose raw storage layout as a public interface; serve media through explicit endpoints.
- **Identity Pattern:** use `Identity<S, ID>` with `Unsaved` and `Persisted`.
- **Weak association across roots:** relate roots by IDs or saved roots, not embedded aggregates.
- **Optimized reads:** use summaries for lists and aggregates for detail views.
