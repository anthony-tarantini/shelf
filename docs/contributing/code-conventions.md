<!-- Derived from AGENTS.md and CONTRIBUTING.md — keep in sync -->

# Code Conventions

## Formatting

Shelf uses **Spotless** with the `ktfmt` formatter. Check or fix style with:

```bash
./gradlew spotlessCheck    # verify
./gradlew spotlessApply    # auto-fix
```

## Type Safety

- Use domain value classes for all primitives. Never use raw `String` or `UUID` where a specialized type exists.
- Use the `Identity<S, ID>` pattern for entity IDs to model persistence states at the type level.
- Factory functions return `Either<AppError, T>` for untrusted input; `fromRaw` is for trusted database rehydration only.
- Unwrap value classes via `.value` at the last moment in the persistence layer.

## Error Handling

- Use Arrow's `Either`/`Raise` DSL and `AppError` subtypes. No exceptions in business logic.
- Prefer Arrow's `catch { ... }` over manual try/catch.
- Use `context(_: RaiseContext)` for implicit error propagation.

## Domain Commands and Mutations

- For complex mutations, map API DTOs to validated domain commands at the route boundary.
- Prefer sealed/domain-specific mutation types over boolean-flag state combinations.
- Services follow: `load -> domain decider/aggregate decisions -> repository persist -> domain event handling`.

## Routing

- Routes stay thin: auth extraction + DTO mapping only.
- Read projections (`Aggregate -> Summary`) and asset selection belong in service/provider interfaces.
- Do not put business policy in routes.

## Persistence

- Persistence code is strictly typed I/O. No authorization or workflow logic in persistence.
- Authorization policy lives in services or dedicated policy helpers.

## Series Identity

Do not assume `series.title` is globally unique. Resolve and link series through explicit IDs or scoped policies.

## Authorization

- Use named policy helpers: `sharedCatalogRead`, `sharedCatalogMutation`, `requireOwnership`, `requireAdmin`.
- Prefer shared auth wrappers from `user/auth/*` over custom credential parsing in route files.

## Frontend

### i18n

- Put feature-specific strings in matching namespace files (`import`, `admin`, `books`, `settings`).
- Use shared copy in `common.json` only when truly reusable across domains.
- Keep key names semantic, not presentational.

### Mobile and PWA

- Treat the authenticated UI as mobile-first.
- Preserve the desktop sidebar on large screens; use top bar, bottom tabs, and drawer for phone navigation.
- Keep install and update flows separate. `ReloadPrompt` handles service-worker updates; install prompts are separate.
- Do not rely on iOS Safari emitting `beforeinstallprompt`.

## Observability

- Keep telemetry low-cardinality. Never add metric tags or span attributes with user IDs, tokens, filenames, raw search strings, or entity IDs.
- Keep logging output mode env-gated via `OBSERVABILITY_JSON_LOGS_ENABLED`.
- If enabling the JVM OTEL agent, ensure only one side owns inbound Ktor server spans.

## Security Boundaries

- Treat HTTP input, upload metadata, filesystem metadata, and external API payloads as untrusted until validated.
- Do not expose raw storage directories as public HTTP paths.
- Use `fromRaw` only for trusted rehydration paths.
