# Shelf 📚

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Shelf is a modern, type-safe book management backend built with **Kotlin**, **Ktor**, **SQLDelight**, and **Arrow**. It follows strict Functional Domain-Driven Design (DDD) principles to provide a robust and maintainable foundation for your personal digital library.

## Screenshots

### Desktop

![Desktop frontpage](docs/desktop-frontpage.png)
![Desktop import flow](docs/desktop-import.png)
![Desktop search](docs/desktop-search.png)

### Mobile

![Mobile frontpage](docs/mobile-frontpage.png)
![Mobile search](docs/mobile-search.png)

## 🚀 Features

- **State-Aware Domain:** Custom `Identity<S, ID>` wrapper modeling persistence states (`Unsaved` vs `Persisted`) at the type level.
- **Strict DDD Hierarchy:** Roots, Records, and Aggregates to clarify data ownership and lifecycle.
- **Scoped Series Identity:** Series titles are not treated as globally unique; linking is ID/policy-driven.
- **Type-Safe Primitives:** Kotlin value classes and Arrow validation for domain primitives (IDs, Emails, StoragePaths).
- **Validated Domain Commands:** Complex write flows map API DTOs to validated domain commands at the route boundary.
- **Decider-Driven Mutations:** Complex write orchestration uses `load -> domain decider/aggregate -> persist -> domain event` to keep service flows explicit.
- **Thin Route Boundaries:** Route handlers stay focused on auth + DTO mapping, while read projections and asset selection live in service/provider layers.
- **Illegal-State Mutation Models:** Domain mutation outputs prefer ADTs/sealed types over boolean flag combinations.
- **Functional Error Handling:** Arrow's `Raise` DSL for clean, linear error flows without exceptions.
- **Structured Persistence:** Type-safe SQL with SQLDelight and transactional aggregate persistence.
- **Directory Scanning:** Recursive server-side scanning for media files (EPUB, MP3) with automatic metadata extraction.
- **JWT Authentication:** Secure user identity and authenticated routes with full privacy scoping.
- **Private Media Access:** Book files are served through explicit API routes instead of raw disk mounts.
- **Explicit Access Policy:** Shared catalog access and user-owned resource checks use named policy helpers rather than ad hoc authorization branches.
- **Optimized API:** `Summary` views for listing; comprehensive `Aggregate` views for detail pages.
- **Responsive Web UI:** Mobile-first catalog and reader shell with dedicated phone navigation.
- **PWA Install UX:** Manifest + service worker support with explicit Android install CTA and iOS Add to Home Screen guidance.

## 🛠️ Tech Stack

- **Language:** [Kotlin 2.x](https://kotlinlang.org/) (JVM)
- **Framework:** [Ktor](https://ktor.io/)
- **Functional Library:** [Arrow 2.x](https://arrow-kt.io/)
- **Database:** [PostgreSQL](https://www.postgresql.org/)
- **Persistence:** [SQLDelight](https://sqldelight.github.io/sqldelight/)
- **Build Tool:** [Gradle](https://gradle.org/) (KTS)
- **Environment:** [Docker Compose](https://docs.docker.com/compose/) for local development.

## 🏁 Quick Start

For detailed installation and usage instructions, please refer to the [Deployment Guide](docs/Deployment-Guide.md).

### Prerequisites
- JDK 21+
- Docker & Docker Compose

### Local Development
1. **Start the database:**
   ```bash
   docker-compose up -d
   ```

> **⚠️ CRITICAL SECURITY WARNING:** The `example.env` file contains default credentials and secrets. You MUST change `JWT_SECRET`, `ENCRYPTION_SECRET`, and `POSTGRES_PASSWORD` before deploying to a public-facing server.

2. **Run the application:**
   ```bash
   ./gradlew run
   ```
   The server will start at `http://localhost:8080`.

### Compose Modes

Shelf has two compose entry points:

- `docker-compose.yaml`
  - image-based stack for production-like local runs
  - frontend on `http://localhost:3000`
- `dev.docker-compose.yaml`
  - bind-mounted source with hot reload for frontend and backend
  - frontend on `http://localhost:4173`

Start the dev stack with:

```bash
podman compose -f dev.docker-compose.yaml up --build
```

Do not run `docker-compose.yaml` and `dev.docker-compose.yaml` at the same time on one machine. They intentionally reuse backend, database, Valkey, and LGTM ports.

In dev compose:

- backend runs `./gradlew run --continuous`
- frontend runs `bun run dev -- --host 0.0.0.0 --port 4173`
- source changes reload without rebuilding the containers

### Security Notes
- Media files are private by default and are served through API endpoints, not direct storage mounts.
- Directory scanning is an administrative operation and is limited to configured import roots.
- Set `IMPORT_SCAN_ROOTS` to a comma-separated list of allowed scan directories for production deployments.

## 📖 Documentation

Full documentation is available at **[anthony-tarantini.github.io/shelf](https://anthony-tarantini.github.io/shelf)**.

- [Quickstart](https://anthony-tarantini.github.io/shelf/getting-started/quickstart/) — Get Shelf running with Docker Compose.
- [Configuration](https://anthony-tarantini.github.io/shelf/getting-started/configuration/) — All environment variables in one place.
- [User Guide](https://anthony-tarantini.github.io/shelf/user-guide/importing-books/) — Import books, manage metadata, connect devices.
- [Architecture](https://anthony-tarantini.github.io/shelf/architecture/overview/) — Deep dive into the codebase design.
- [Contributing](https://anthony-tarantini.github.io/shelf/contributing/development-setup/) — Development setup and code conventions.

## 🗺️ Roadmap

Shelf is currently in **Active Development (Beta)**. Our upcoming goals include:

- [ ] OPDS 2.0 support.
- [ ] Enhanced metadata providers (OpenLibrary, Google Books).
- [ ] PDF and Comic (CBZ/CBR) support.
- [ ] Multi-user library sharing.

## 📐 Development

If you're interested in contributing or building upon Shelf:

- **[AGENTS.md](AGENTS.md)** — Core philosophy, architectural layers, and golden rules for contributors.
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — File organization conventions and package structure.
- **[CONTRIBUTING.md](CONTRIBUTING.md)** — Guidelines for submitting pull requests and reporting issues.

Backend verification after Kotlin changes:

```bash
./gradlew spotlessApply classes
```

Frontend expectations:

- Keep the authenticated shell responsive first; phone layouts use a top bar, bottom navigation, and drawer instead of the desktop sidebar.
- Treat install UX as platform-specific: Chromium can surface an in-app install CTA from `beforeinstallprompt`, while iOS Safari requires manual Add to Home Screen instructions.

Local KOReader sync smoke test:

```bash
./scripts/koreader-sync-smoke.sh --document-hash <edition-file-hash>
```

If you omit `--document-hash`, the script still verifies token creation and KOReader auth headers end to end.

## Observability

Shelf keeps Cohort for `/readiness` and adds telemetry alongside it:

- Traces: OpenTelemetry via Ktor server instrumentation
- Metrics: Ktor Micrometer metrics plus app-specific counters/timers
- Logs: trace-aware request logging with `trace_id` and `span_id`, with optional JSON output via `OBSERVABILITY_JSON_LOGS_ENABLED=true`
- Metrics endpoint: `/metrics`

Local docker-compose includes Grafana LGTM for OTLP ingestion on `http://localhost:4317` and Grafana on `http://localhost:3001`.

Useful environment variables:

```bash
OBSERVABILITY_ENABLED=true
OBSERVABILITY_TRACING_ENABLED=true
OBSERVABILITY_TRACE_OWNER=app
OBSERVABILITY_METRICS_ENABLED=true
OBSERVABILITY_PROMETHEUS_ENABLED=true
OBSERVABILITY_OTLP_METRICS_ENABLED=true
OBSERVABILITY_JSON_LOGS_ENABLED=false
OTEL_SERVICE_NAME=shelf-backend
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
OTEL_EXPORTER_OTLP_PROTOCOL=grpc
OTEL_JAVAAGENT_ENABLED=false
API_PROXY_TIMEOUT_MS=15000
VITE_API_TIMEOUT_MS=15000
```

`API_PROXY_TIMEOUT_MS` bounds `/api` proxy waits in the frontend server; `VITE_API_TIMEOUT_MS` bounds browser API client waits. Both default to `15000` ms.

For compose runs, prefer the dedicated `COMPOSE_*` and `DEV_COMPOSE_*` variables from [example.env](example.env). Container-to-container endpoints must use service names like `backend`, `database`, `valkey`, and `lgtm`, not `localhost`.

Quick verification:

```bash
curl -sS http://localhost:8080/readiness
curl -sS http://localhost:8080/metrics | head
```

### JVM OTEL Agent Modes

Supported tracing ownership modes:

- `OBSERVABILITY_TRACE_OWNER=app`
  - Shelf owns Ktor server spans in-process.
  - Recommended default.
- `OBSERVABILITY_TRACE_OWNER=agent`
  - Disable in-app Ktor server tracing and let the JVM agent own inbound HTTP spans.

Recommended hybrid mode:

- `OBSERVABILITY_TRACE_OWNER=app`
- `OTEL_JAVAAGENT_ENABLED=true`
- leave `OTEL_INSTRUMENTATION_KTOR_ENABLED` unset

In hybrid mode, the backend entrypoint injects the JVM agent and disables agent-side Ktor instrumentation automatically so you keep:

- app-owned Ktor server spans
- agent-owned JDBC, Redis/Lettuce, and outgoing client spans
- no duplicate server spans

### Docker Stack

`docker-compose.yaml` runs the full local stack:

- frontend: `http://localhost:3000`
- backend readiness: `http://localhost:8080/readiness`
- backend metrics: `http://localhost:8080/metrics`
- Grafana LGTM: `http://localhost:3001`

Frontend waits for backend health before starting. Backend health is based on `/readiness`.

Compose-specific networking defaults:

- `COMPOSE_BACKEND_URL=http://backend:8080`
- `COMPOSE_VALKEY_URL=redis://valkey:6379`
- `COMPOSE_OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4317`

Keep plain `localhost` only for host-facing URLs such as browser access to the frontend, backend, or Grafana.

## Releases

GitHub Actions is the source of truth for CI and releases:

- `.github/workflows/ci.yml` runs backend checks, backend tests, frontend unit tests, and publishes `main` images to GitHub Container Registry on pushes to `main`
- `.github/workflows/release-please.yml` maintains a release PR and creates GitHub releases from Conventional Commits

Release metadata is tracked in:

- [version.txt](version.txt)
- [CHANGELOG.md](CHANGELOG.md)
- [release-please-config.json](release-please-config.json)
- [.release-please-manifest.json](.release-please-manifest.json)

If you want CI to run on release PRs opened by `release-please`, configure a fine-grained GitHub token and swap it in for `secrets.GITHUB_TOKEN` in the workflow. GitHub does not trigger follow-up workflows from PRs or tags created by the default repository token.

### Dev Docker Stack

`dev.docker-compose.yaml` is the hot-reload stack:

- frontend: `http://localhost:4173`
- backend readiness: `http://localhost:8080/readiness`
- backend metrics: `http://localhost:8080/metrics`
- Grafana LGTM: `http://localhost:3001`

Dev-specific networking defaults:

- `DEV_COMPOSE_BACKEND_URL=http://backend:8080`
- `DEV_COMPOSE_VALKEY_URL=redis://valkey:6379`
- `DEV_COMPOSE_OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4317`

Use this when you want bind-mounted source and live reload without rebuilding the images after each code change.

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to get started and the standards we expect.

## 📄 License

This project is licensed under the [MIT License](LICENSE).
