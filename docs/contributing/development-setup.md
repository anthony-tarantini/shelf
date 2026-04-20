<!-- Derived from CONTRIBUTING.md — keep in sync -->

# Development Setup

## Prerequisites

- JDK 21+
- Docker and Docker Compose
- [Bun](https://bun.sh/) (for frontend development)

## Getting Started

1. Clone the repository:

    ```bash
    git clone https://github.com/tarantini-io/shelf.git
    cd shelf
    ```

2. Copy the environment file:

    ```bash
    cp example.env .env
    ```

3. Start the database:

    ```bash
    docker-compose up -d database
    ```

4. Run the backend:

    ```bash
    ./gradlew run
    ```

    The server starts at `http://localhost:8080`.

## Compose Modes

Shelf ships with two compose entry points. **Do not run both simultaneously** — they share ports.

### Production-like (`docker-compose.yaml`)

Image-based stack:

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- Grafana: `http://localhost:3001`

### Hot-reload (`dev.docker-compose.yaml`)

Bind-mounted source with live reload:

```bash
podman compose -f dev.docker-compose.yaml up --build
```

- Frontend: `http://localhost:4173` (runs `bun run dev`)
- Backend: `http://localhost:8080` (runs `./gradlew run --continuous`)
- Source changes reload without rebuilding containers

### Compose Networking

Container-to-container endpoints use service names (`backend`, `database`, `valkey`, `lgtm`). Use the dedicated `COMPOSE_*` or `DEV_COMPOSE_*` env vars from `example.env`. Do not point compose services at `localhost`.

## Verification

After Kotlin changes:

```bash
./gradlew spotlessApply classes
```

Run all tests:

```bash
./gradlew test
```

## Local Integration Smoke Tests

### KOReader Sync

```bash
./scripts/koreader-sync-smoke.sh --document-hash <edition-file-hash>
```

Omitting `--document-hash` still verifies token creation and KOReader auth headers end to end.

### Health and Metrics

```bash
curl -sS http://localhost:8080/readiness
curl -sS http://localhost:8080/metrics | head
```
