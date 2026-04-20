<!-- Derived from README.md — keep in sync -->

# Observability

Shelf ships with built-in observability through OpenTelemetry, Prometheus, and structured logging.

## Components

| Component | Description |
|-----------|-------------|
| **Traces** | OpenTelemetry via Ktor server instrumentation |
| **Metrics** | Ktor Micrometer metrics plus app-specific counters/timers |
| **Logs** | Trace-aware request logging with `trace_id` and `span_id` |
| **Health** | `/readiness` endpoint via Cohort |
| **Metrics endpoint** | `/metrics` (Prometheus scrape target) |

## Environment Variables

### Core

| Variable | Default | Description |
|----------|---------|-------------|
| `OBSERVABILITY_ENABLED` | `true` | Master toggle for observability |
| `OBSERVABILITY_ENVIRONMENT` | `development` | Environment label for telemetry |

### Tracing

| Variable | Default | Description |
|----------|---------|-------------|
| `OBSERVABILITY_TRACING_ENABLED` | `true` | Enable distributed tracing |
| `OBSERVABILITY_TRACE_OWNER` | `app` | Who owns inbound Ktor server spans: `app` or `agent` |
| `OBSERVABILITY_TRACE_SAMPLING_RATIO` | `1.0` | Trace sampling ratio (0.0 to 1.0) |

### Metrics

| Variable | Default | Description |
|----------|---------|-------------|
| `OBSERVABILITY_METRICS_ENABLED` | `true` | Enable metrics collection |
| `OBSERVABILITY_PROMETHEUS_ENABLED` | `true` | Expose `/metrics` Prometheus endpoint |
| `OBSERVABILITY_OTLP_METRICS_ENABLED` | `true` | Export metrics via OTLP |
| `OBSERVABILITY_METRICS_PATH` | `/metrics` | Prometheus scrape path |

### Logging

| Variable | Default | Description |
|----------|---------|-------------|
| `OBSERVABILITY_JSON_LOGS_ENABLED` | `false` | Enable JSON log output (line format is the default) |

### OpenTelemetry

| Variable | Default | Description |
|----------|---------|-------------|
| `OTEL_SERVICE_NAME` | `shelf-backend` | Service name in traces/metrics |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OTLP collector endpoint |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` | OTLP transport protocol |
| `OTEL_JAVAAGENT_ENABLED` | `false` | Enable JVM OpenTelemetry agent |

## JVM OTEL Agent Modes

### App-owned spans (recommended default)

```bash
OBSERVABILITY_TRACE_OWNER=app
```

Shelf owns Ktor server spans in-process.

### Agent-owned spans

```bash
OBSERVABILITY_TRACE_OWNER=agent
```

Disables in-app Ktor server tracing; the JVM agent owns inbound HTTP spans.

### Hybrid mode (recommended for full visibility)

```bash
OBSERVABILITY_TRACE_OWNER=app
OTEL_JAVAAGENT_ENABLED=true
# leave OTEL_INSTRUMENTATION_KTOR_ENABLED unset
```

In hybrid mode, the backend entrypoint injects the JVM agent and disables agent-side Ktor instrumentation automatically. This gives you:

- App-owned Ktor server spans
- Agent-owned JDBC, Redis/Lettuce, and outgoing client spans
- No duplicate server spans

## Local Grafana Stack

The Docker Compose stack includes Grafana LGTM for local observability:

- **OTLP ingestion:** `http://localhost:4317`
- **Grafana dashboard:** `http://localhost:3001`

### Compose Networking

Container-to-container endpoints use service names:

```bash
COMPOSE_OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4317
```

Keep `localhost` only for host-facing URLs (browser access to Grafana).

## Quick Verification

```bash
curl -sS http://localhost:8080/readiness
curl -sS http://localhost:8080/metrics | head
```

## Telemetry Hygiene

Observability labels, span attributes, and logs must stay **low-cardinality**. Never emit:

- User IDs or usernames
- Tokens or credentials
- Raw search strings
- Filenames or book IDs
- Any high-cardinality value as a metric tag or span attribute
