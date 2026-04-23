# Contributing to Shelf 🤝

First off, thank you for considering contributing to Shelf! It's people like you who make the open-source community such an amazing place to learn, inspire, and create.

## 📜 Our Philosophy

This project follows a strict **Domain-Driven Design (DDD)** approach. Before making any changes, please read [AGENTS.md](AGENTS.md) carefully. It outlines our core design principles, specifically the "Make Illegal States Unrepresentable" philosophy.

## 🛠️ Development Workflow

### 1. Set Up Your Environment
-   Ensure you have JDK 21+ and Docker installed.
-   Clone the repository.
-   Start the local database using `docker-compose up -d`.
-   Set an `ENCRYPTION_SECRET` for integration credential encryption. In local development you can copy it from `example.env`, but production must use a unique secret distinct from `JWT_SECRET`.
-   When running the full compose stack, use the dedicated `COMPOSE_*` env vars from `example.env` for container-to-container addresses. Do not point compose services at `localhost` for Postgres, Valkey, backend, or LGTM.
-   Use `dev.docker-compose.yaml` for bind-mounted hot reload and keep `docker-compose.yaml` for the image-based stack.
-   Do not run both compose modes simultaneously unless you also remap their ports.
-   Podcast ingestion from walled gardens is Libation-container-only. Use the shared drop directory (`./data/libation-export`) and avoid reintroducing direct Audible login flows in Shelf.

### 2. Coding Standards
-   **Style:** We use **Spotless** with the `ktfmt` formatter to maintain consistent code style.
-   **Validation:** You can run `./gradlew spotlessCheck` to verify your code style or `./gradlew spotlessApply` to automatically fix it.
-   **Type Safety:** Always use domain value classes for primitives. Use the `Identity<S, ID>` pattern for entity IDs to explicitly model persistence states. Never use raw `String` or `UUID` where a specialized type exists.
-   **Mutation Modeling:** Prefer sealed/domain-specific mutation types over boolean-flag state combinations in domain decisions.
-   **Series Modeling:** Do not assume series title uniqueness across the whole catalog; use IDs/scoped resolution rules.
-   **Boundary Mapping:** For complex mutations, keep request DTOs at the route boundary and map them into validated domain commands before service calls.
-   **Thin Routes:** Keep read projections and asset/path selection logic in service/provider interfaces; route files should remain boundary adapters.
-   **Orchestration Pattern:** For complex write paths, prefer `load -> domain decider/aggregate decisions -> repository persist -> domain event handling` over monolithic service scripts.
-   **Typed Error Flows:** Do not throw ad hoc exceptions from domain/service logic for expected behavior; use typed `AppError` + `RaiseContext`.

### 3. Testing
-   Every new feature or bug fix **must** include automated tests.
-   We use **Kotest** for our test suite.
-   Run all tests using `./gradlew test` before submitting a pull request.
-   After Kotlin changes, run `./gradlew spotlessApply classes`.
-   Use the [Test Ownership Matrix](https://anthony-tarantini.github.io/shelf/contributing/testing/) to keep each decision rule tested in one primary layer and avoid duplicate assertions.

### 4. Frontend i18n Conventions
-   Do not add new user-facing strings directly in Svelte components when they belong in locale files.
-   Prefer shared copy in `ui/src/lib/i18n/en/common.json` only when the wording is truly reusable across domains.
-   Put feature- or route-specific strings in the matching namespace such as `import`, `admin`, `books`, or `settings`.
-   Keep namespaces shallow and intentional. Avoid one-off top-level files unless a feature has enough surface area to justify it.
-   Keep key names semantic, not presentational. Prefer `import.ingest.scan.success_title` over `import.green_box_title`.
-   When adding a new locale namespace, register it in `ui/src/lib/i18n.ts` and keep the default English file complete.

### 5. Frontend Mobile and PWA Conventions
-   Treat the authenticated UI as mobile-first. Preserve the desktop sidebar on large screens, but keep phone navigation in the shared top bar, bottom tabs, and drawer.
-   Avoid `h-screen` shells for primary app views unless the route truly needs a viewport-locked experience; prefer dynamic viewport-safe sizing and safe-area padding.
-   Keep install/update flows separate. `ReloadPrompt` handles service-worker updates; install prompts and iOS Add to Home Screen guidance belong in the dedicated install-state UI.
-   Do not rely on iOS Safari to emit `beforeinstallprompt`; if install guidance is needed on iPhone/iPad Safari, provide explicit in-app instructions.

### 6. Local Integration Smoke Tests
-   For KOReader sync development, prefer the local smoke script in `scripts/koreader-sync-smoke.sh` before testing on physical hardware.
-   Preserve Cohort for `/readiness`; observability is additive and must not replace readiness checks.
-   Keep telemetry low-cardinality. Do not add metric tags or span attributes with user IDs, tokens, filenames, raw search strings, or entity IDs.
-   Keep logging output mode env-gated. Use `OBSERVABILITY_JSON_LOGS_ENABLED=true` for JSON logs and keep line logs as the default fallback.
-   After backend changes that touch telemetry, verify both `/readiness` and `/metrics`.
-   If enabling the JVM OTEL agent, ensure only one side owns inbound Ktor server spans. The supported default is `OBSERVABILITY_TRACE_OWNER=app` with agent Ktor instrumentation disabled.
-   For compose-based telemetry, use `COMPOSE_OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4317`; `localhost` inside the backend container points back to the backend container itself.
-   For hot-reload compose runs, use the `DEV_COMPOSE_*` overrides and `podman compose -f dev.docker-compose.yaml up --build`.
-   The script verifies token auth automatically and can also exercise progress sync if you provide an edition `fileHash`.

### 7. Security and Architecture Boundaries
-   Treat HTTP input, upload metadata, filesystem metadata, and external API payloads as untrusted until validated.
-   Do not expose raw storage directories as public HTTP paths; serve media through explicit routes.
-   Keep authorization policy in services or dedicated policy helpers, not in persistence code.
-   Prefer the named access-policy helpers in `user/auth/policy.kt` and route wrappers in `user/auth/auth.kt` over ad hoc `ensure(...)` checks.
-   Use `fromRaw` only for trusted rehydration paths.

### 8. Submitting Changes
-   **Branching:** Create a descriptive branch name (e.g., `feature/add-series-support` or `fix/author-slug-validation`).
-   **Commits:** This project uses **Conventional Commits** to automate releases and changelogs. Please format your commit messages as follows:
    -   `feat: <description>` — For new features (triggers a MINOR version bump).
    -   `fix: <description>` — For bug fixes (triggers a PATCH version bump).
    -   `chore: <description>` — For maintenance (no version bump).
    -   `docs: <description>` — For documentation changes (no version bump).
    -   `BREAKING CHANGE: <description>` — In the footer or body (triggers a MAJOR version bump).
    -   GitHub releases are managed by `release-please`, which opens and updates release PRs from these commit messages.
-   **Pull Requests:** 
    -   Provide a clear description of the changes.
    -   Reference any related issues.
    -   Ensure all CI checks (build, lint, tests) pass.

## 🏗️ Project Structure

-   `src/main/kotlin/io/tarantini/shelf/`: Core application logic organized by domain (Author, Book, Series, User).
-   `src/main/sqldelight/`: Database schemas and queries.
-   `src/test/kotlin/`: Integration and unit tests.

## ❓ Questions?

If you have any questions or need clarification on the architecture, feel free to open an issue or reach out to the maintainers.

Happy coding! 🚀
