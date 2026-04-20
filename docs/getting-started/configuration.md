# Configuration Reference

All configuration is done through environment variables. Copy `example.env` to `.env` and adjust values for your deployment.

## Application

| Variable | Default | Description |
|----------|---------|-------------|
| `HOST` | `0.0.0.0` | Bind address for the backend server |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `CORS_ALLOWED_HOSTS` | `localhost:3000,localhost:4173` | Comma-separated list of allowed CORS origins |
| `PUBLIC_ROOT_URL` | `http://localhost:3000` | Public URL of the frontend (used for links in OPDS, emails, etc.) |
| `BACKEND_URL` | `http://localhost:8080` | Backend URL for frontend-to-backend communication |

## Database

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/shelf` | PostgreSQL JDBC connection URL |
| `POSTGRES_USERNAME` | `postgres` | Database username |
| `POSTGRES_PASSWORD` | `postgres` | Database password |

!!! danger "Security"
    Change `POSTGRES_PASSWORD` from the default before any public-facing deployment.

## Authentication

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | `MySuperStrongSecret` | Secret key for signing JWT tokens |
| `JWT_ISSUER` | `Shelf` | JWT issuer claim |
| `JWT_DURATION` | `30` | JWT token duration (minutes) |

!!! danger "Security"
    Change `JWT_SECRET` from the default before any public-facing deployment. Use a long, random string.

## Storage

| Variable | Default | Description |
|----------|---------|-------------|
| `STORAGE_PATH` | `./storage` | Path where Shelf stores processed files (covers, assets) |
| `IMPORT_SCAN_ROOTS` | — | Comma-separated list of directories Shelf is allowed to scan for imports |

## External Services

| Variable | Default | Description |
|----------|---------|-------------|
| `HARDCOVER_URL` | `https://api.hardcover.app/v1/graphql` | Hardcover GraphQL API endpoint |
| `HARDCOVER_API_KEY` | — | Your Hardcover API key for metadata lookups |
| `VALKEY_URL` | `redis://localhost:6379` | Valkey/Redis connection URL for caching |
| `PUBLIC_KOREADER_BASE_URL` | — | Public URL for KOReader sync endpoint (if different from main URL) |

## Observability

See the [Observability](../architecture/observability.md) page for detailed configuration of tracing, metrics, and logging.

| Variable | Default | Description |
|----------|---------|-------------|
| `OBSERVABILITY_ENABLED` | `true` | Master toggle |
| `OBSERVABILITY_TRACING_ENABLED` | `true` | Enable distributed tracing |
| `OBSERVABILITY_TRACE_OWNER` | `app` | Span ownership: `app` or `agent` |
| `OBSERVABILITY_METRICS_ENABLED` | `true` | Enable metrics collection |
| `OBSERVABILITY_PROMETHEUS_ENABLED` | `true` | Expose Prometheus `/metrics` endpoint |
| `OBSERVABILITY_OTLP_METRICS_ENABLED` | `true` | Export metrics via OTLP |
| `OBSERVABILITY_METRICS_PATH` | `/metrics` | Prometheus scrape path |
| `OBSERVABILITY_TRACE_SAMPLING_RATIO` | `1.0` | Trace sampling ratio |
| `OBSERVABILITY_JSON_LOGS_ENABLED` | `false` | Enable JSON log output |
| `OBSERVABILITY_ENVIRONMENT` | `development` | Environment label |
| `OTEL_SERVICE_NAME` | `shelf-backend` | Service name in traces/metrics |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OTLP collector endpoint |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` | OTLP transport protocol |
| `OTEL_JAVAAGENT_ENABLED` | `false` | Enable JVM OTEL agent |

## Docker Compose Overrides

When running via Docker Compose, container-to-container communication must use service names, not `localhost`. Use these dedicated overrides in your `.env`:

### Production Compose (`docker-compose.yaml`)

| Variable | Default | Description |
|----------|---------|-------------|
| `COMPOSE_IMPORT_SCAN_ROOTS` | `/storage` | Import scan roots inside the container |
| `COMPOSE_STORAGE_PATH` | `/storage` | Storage path inside the container |
| `COMPOSE_VALKEY_URL` | `redis://valkey:6379` | Valkey URL using service name |
| `COMPOSE_BACKEND_URL` | `http://backend:8080` | Backend URL using service name |
| `COMPOSE_CORS_ALLOWED_HOSTS` | `localhost:3000` | CORS for production frontend |
| `COMPOSE_PUBLIC_ROOT_URL` | `http://localhost:3000` | Public frontend URL |
| `COMPOSE_OTEL_EXPORTER_OTLP_ENDPOINT` | `http://lgtm:4317` | OTLP endpoint using service name |

### Dev Compose (`dev.docker-compose.yaml`)

| Variable | Default | Description |
|----------|---------|-------------|
| `DEV_COMPOSE_IMPORT_SCAN_ROOTS` | `/workspace/storage` | Import scan roots in dev container |
| `DEV_COMPOSE_STORAGE_PATH` | `/workspace/storage` | Storage path in dev container |
| `DEV_COMPOSE_VALKEY_URL` | `redis://valkey:6379` | Valkey URL using service name |
| `DEV_COMPOSE_BACKEND_URL` | `http://backend:8080` | Backend URL using service name |
| `DEV_COMPOSE_CORS_ALLOWED_HOSTS` | `localhost:4173` | CORS for dev frontend |
| `DEV_COMPOSE_PUBLIC_ROOT_URL` | `http://localhost:4173` | Public dev frontend URL |
| `DEV_COMPOSE_OTEL_EXPORTER_OTLP_ENDPOINT` | `http://lgtm:4317` | OTLP endpoint using service name |
