FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy Gradle wrapper and configuration
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .
COPY config config

# Download dependencies (caching layer)
RUN ./gradlew --no-daemon dependencies

# Copy source code and build
COPY src src
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
ARG OTEL_JAVA_AGENT_VERSION=2.18.1
RUN apk add --no-cache ffmpeg wget
WORKDIR /app
COPY --from=builder /app/build/install/shelf /app/
RUN wget -O /opt/opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar"
COPY scripts/backend-entrypoint.sh /app/bin/backend-entrypoint.sh
RUN chmod +x /app/bin/backend-entrypoint.sh
EXPOSE 8080
CMD ["/app/bin/backend-entrypoint.sh"]
