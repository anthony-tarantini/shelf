# AGENTS.md — Project Philosophy & Design Principles

## Core Philosophy
**Make illegal states unrepresentable** via Kotlin types and Arrow primitives. Once an object enters the domain layer, it is guaranteed valid and consistent.

---

## Mandatory Mandates (Non-Negotiable)
1. **Contextual Architecture:** Read this file before designing/changing anything structural.
2. **Documentation Sync:** Update AGENTS.md, README.md, CONTRIBUTING.md when introducing new patterns.
3. **Verification:** After Kotlin changes run `./gradlew spotlessApply classes`.

---

## Architectural Layers
| Layer | Responsibility | Key Notes |
|-------|----------------|-----------|
| Routing (`routes.kt`) | HTTP boundary; maps raw JSON DTOs → API Request types | For complex mutations, map API DTOs to validated domain commands before calling services; keep read projections and asset selection in Services |
| Service (`service.kt`) | Orchestrator & gatekeeper; **only** layer with business logic | Coordinates repositories/deciders via `context(_: RaiseContext)`; avoid parsing transport DTOs directly |
| Repository (`repository.kt`) | Mutation port interface for service-layer testability | Thin interface over persistence; implementations delegate to `*Queries` extension functions |
| Persistence (`persistence.kt` / `.sq`) | SQLDelight extension functions; strictly typed entities & transactions | Save/load aggregates as single unit |

---

## Domain Modeling Essentials

### Identity Pattern
- **`Identity<S, ID>`**: Wraps entity IDs with state awareness.
  - `Unsaved`: ID is logically `Nothing`; compiler prevents use.
  - `Persisted`: ID guaranteed present; access via `.id`.
- Use `SavedRoot` for persisted data; `NewRoot` for incoming data.

### Hierarchy (Root → Record → Aggregate → Summary)
| Term | Meaning | Example |
|------|---------|---------|
| **Root** | Entry point of an Aggregate; standalone identity | `BookRoot`, `AuthorRoot` |
| **Record** | Subordinate data describing a Root | `MetadataRecord` |
| **Aggregate** | Full tree: Root + Records + Children | `BookAggregate` |
| **Summary** | Flattened, read-optimized view for lists | `BookSummary` |

**Relationships:** Strong composition (Root owns Record) vs. weak association (Root relates to Root by ID/SavedRoot only).

### Standard Domain Layout
Each domain module follows this file structure:
```
domain/
  api.kt          — request DTOs and request→command mappers
  commands.kt     — validated command types + value objects (e.g., AuthorName)
  decider.kt      — pure decision functions: (snapshot, command) → decision/mutation
  error.kt        — domain-specific AppError subtypes
  models.kt       — Root, Aggregate, Summary, Record types
  primitives.kt   — ID value classes and other domain primitives
  normalization.kt — canonicalization helpers (optional, where needed)
  events.kt       — domain event types (optional, where needed)
```

### Value Classes
- Use `@JvmInline value class` for domain primitives (IDs, Emails, Paths).
- Private constructor + factory returning `Either<AppError, T>`.
- Provide `fromRaw` for trusted DB rehydration. Unwrap via `.value` at last moment in Persistence.

---

## Error Handling
Use Arrow Either/AppError + Raise DSL (`either { }`, `.bind()`); no exceptions in business logic. Prefer Arrow's `catch { ... }` over manual try/catch.

## Trust Boundaries
- HTTP input, upload metadata, filesystem metadata, and external provider data are **untrusted** until validated.
- `fromRaw` is only for trusted DB rehydration or already-sanitized internal values.
- Storage paths must remain relative to the configured storage root; never expose raw disk layout as a public contract.
- Private media should be served through explicit endpoints with policy checks. Do not mount storage directories directly for convenience.
- Integration credentials must be encrypted at rest (AES-GCM with per-row IV); never log plaintext credentials, ciphertext blobs, or IV values.
- Encode access rules with named policy helpers:
  `sharedCatalogRead`, `sharedCatalogMutation`, `requireOwnership`, `requireAdmin`.

---

## Golden Rules for Agents
1. **ALWAYS** use Identity pattern; never embed full aggregates across roots.
2. **NEVER** merge fields or add complex logic in Persistence — move to Service.
3. **ALWAYS** leverage `context(_: RaiseContext)` for implicit error propagation.
4. **ALWAYS** unwrap Value Classes at last moment; trust `fromRaw`.
5. **Sub-resource routing:** Use Summaries for lists, Aggregates for details; prefer dedicated sub-resource routes (e.g., `/books/{id}/authors`) for large collections.
6. **Flattened serialization:** Ensure UI receives raw IDs/nulls via IdentitySerializer.
7. **Authorization policy:** Routes may extract identity, but ownership/admin decisions belong in Services or explicit policy helpers, not Persistence.
8. **Asset selection:** Keep canonical cover/edition/file resolution in Services rather than ad hoc route branches.
9. **Integration auth:** OPDS and KOReader should use shared auth wrappers from `user/auth/*` instead of custom credential parsing in route files.
10. **Frontend shell:** Keep authenticated app navigation responsive-first; desktop may use sidebars, but mobile must use explicit top/bottom navigation and safe-area-aware layouts.
11. **PWA UX:** Treat installability as platform-specific. Chromium may use `beforeinstallprompt`; iOS Safari requires manual Add to Home Screen guidance and should not rely on a browser install prompt.
12. **Telemetry hygiene:** Observability labels, span attributes, and logs must stay low-cardinality. Never emit user IDs, usernames, tokens, raw search strings, filenames, or book IDs as metric tags or span attributes.
13. **Trace ownership:** When using the JVM OpenTelemetry agent, keep exactly one owner for inbound Ktor server spans. Prefer app-owned server tracing and agent-owned library/client spans.
14. **Compose networking:** In Docker/Podman Compose, container-to-container endpoints must use service names (`backend`, `database`, `valkey`, `lgtm`). Keep host-local `localhost` values out of compose service wiring by using dedicated `COMPOSE_*` env overrides.
15. **Compose modes:** Keep image-based and hot-reload compose flows separate. Use `docker-compose.yaml` for production-like container images and `dev.docker-compose.yaml` for bind-mounted live development.
16. **Release automation:** GitHub Actions and `release-please` are the release source of truth. Keep `version.txt`, `CHANGELOG.md`, and release workflow/config files aligned with Conventional Commit based releases.
17. **Mutation orchestration:** For complex write paths, services should follow `load -> domain decider/aggregate apply -> persist -> domain event handling` instead of embedding all mutation decisions inline.
18. **Mutation types:** Prefer sealed/domain-specific mutation types over flag+list combinations so invalid mutation combinations are not representable.
19. **Series identity:** Do not assume `series.title` is globally unique. Resolve/link series through explicit IDs or scoped policies.
20. **Mutation repositories:** Domains with decider-driven mutations should expose a `*MutationRepository` interface for testability. Services accept the repository as a parameter with a default implementation. Read-only queries may stay as direct `*Queries` calls.
21. **Route thinness:** Routes should avoid business projections (`Aggregate -> Summary`) and ad hoc error folds. Put these in Service/provider interfaces so routes stay boundary-only.
22. **Logging format rollout:** Keep backend JSON logging env-gated through `OBSERVABILITY_JSON_LOGS_ENABLED`; line format remains the safe default for local readability.
23. **Podcast data ownership:** Podcast episodes are podcast-domain records. Do not persist podcast episode ingestion data in `books`/`editions`; keep podcast storage/query models isolated.
24. **Libation cross-run identity:** For Libation imports, resolve existing podcast/series via persisted canonical title keys before creating new roots; do not rely on per-run memory only.
25. **Encryption secret policy:** `ENCRYPTION_SECRET` may fall back only in local/development environments; non-dev startup must fail if it is missing.
