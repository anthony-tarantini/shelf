#!/bin/sh
set -eu

JAVA_AGENT_PATH="${OTEL_JAVAAGENT_PATH:-/opt/opentelemetry-javaagent.jar}"
TRACE_OWNER="${OBSERVABILITY_TRACE_OWNER:-app}"

if [ "${OTEL_JAVAAGENT_ENABLED:-false}" = "true" ]; then
  JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -javaagent:${JAVA_AGENT_PATH}"

  if [ "${TRACE_OWNER}" = "app" ] && [ -z "${OTEL_INSTRUMENTATION_KTOR_ENABLED:-}" ]; then
    export OTEL_INSTRUMENTATION_KTOR_ENABLED=false
  fi

  if [ "${TRACE_OWNER}" = "agent" ] && [ -z "${OBSERVABILITY_TRACING_ENABLED:-}" ]; then
    export OBSERVABILITY_TRACING_ENABLED=false
  fi

  export JAVA_TOOL_OPTIONS
fi

exec /app/bin/shelf
