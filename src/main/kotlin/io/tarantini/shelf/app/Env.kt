package io.tarantini.shelf.app

import java.lang.System.getenv
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private const val DEFAULT_PORT: Int = 8080
private const val DEFAULT_ISSUER: String = "Shelf"
private const val DEFAULT_DURATION: Int = 30
private const val DEFAULT_JDBC_DRIVER: String = "org.postgresql.Driver"

private fun requireEnv(name: String): String =
    getenv(name)
        ?: throw IllegalStateException("Environment variable $name is required but not set.")

data class Env(
    val dataSource: DataSource,
    val http: Http,
    val auth: Auth,
    val hardcover: Hardcover,
    val storage: Storage,
    val valkey: Valkey,
    val observability: Observability,
    val integration: Integration = Integration.fromEnv(),
) {
    companion object {
        operator fun invoke() =
            Env(
                dataSource = DataSource.fromEnv(),
                http = Http.fromEnv(),
                auth = Auth.fromEnv(),
                hardcover = Hardcover.fromEnv(),
                storage = Storage.fromEnv(),
                valkey = Valkey.fromEnv(),
                observability = Observability.fromEnv(),
                integration = Integration.fromEnv(),
            )
    }

    data class Integration(
        val encryptionSecret: String,
        val libationImportEnabled: Boolean,
        val libationDropDir: String,
        val libationScanIntervalSeconds: Long,
        val minuspodUrl: String,
        val minuspodAdminPassword: String,
    ) {
        companion object {
            fun fromEnv() =
                Integration(
                    encryptionSecret =
                        getenv("ENCRYPTION_SECRET")
                            ?: getenv("JWT_SECRET")
                            ?: "insecure-local-default-change-me",
                    libationImportEnabled =
                        getenv("LIBATION_IMPORT_ENABLED")?.toBooleanStrictOrNull() ?: true,
                    libationDropDir = getenv("LIBATION_DROP_DIR") ?: "./data/libation-export",
                    libationScanIntervalSeconds =
                        getenv("LIBATION_SCAN_INTERVAL_SECONDS")?.toLongOrNull() ?: 300,
                    minuspodUrl = getenv("MINUSPOD_URL") ?: "http://minuspod:8080",
                    minuspodAdminPassword = getenv("MINUSPOD_ADMIN_PASSWORD") ?: "admin",
                )
        }
    }

    data class Valkey(val url: String?) {
        companion object {
            fun fromEnv() = Valkey(url = getenv("VALKEY_URL"))
        }
    }

    data class Storage(val path: String, val scanRoots: List<String>) {
        companion object {
            fun fromEnv(): Storage {
                val path = getenv("STORAGE_PATH") ?: "./storage"
                val scanRoots =
                    getenv("IMPORT_SCAN_ROOTS")
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() } ?: listOf(path)
                return Storage(path = path, scanRoots = scanRoots)
            }
        }
    }

    data class Hardcover(val url: String, val apiKey: String) {
        companion object {
            fun fromEnv() =
                Hardcover(
                    url = getenv("HARDCOVER_URL") ?: "https://api.hardcover.app/v1/graphql",
                    apiKey = requireEnv("HARDCOVER_API_KEY"),
                )
        }
    }

    data class Http(
        val host: String,
        val port: Int,
        val allowedHosts: List<String>,
        val publicRootUrl: String,
    ) {
        companion object {
            fun fromEnv() =
                Http(
                    host = getenv("HOST") ?: "0.0.0.0",
                    port = getenv("SERVER_PORT")?.toIntOrNull() ?: DEFAULT_PORT,
                    allowedHosts =
                        getenv("CORS_ALLOWED_HOSTS")?.split(",")?.map { it.trim() }
                            ?: listOf("localhost:5173"),
                    publicRootUrl = getenv("PUBLIC_ROOT_URL") ?: "http://localhost:8080",
                )
        }
    }

    data class DataSource(
        val url: String,
        val username: String,
        val password: String,
        val driver: String,
    ) {
        companion object {
            fun fromEnv() =
                DataSource(
                    url = requireEnv("POSTGRES_URL"),
                    username = requireEnv("POSTGRES_USERNAME"),
                    password = requireEnv("POSTGRES_PASSWORD"),
                    driver = getenv("POSTGRES_DRIVER") ?: DEFAULT_JDBC_DRIVER,
                )
        }
    }

    data class Auth(val secret: String, val issuer: String, val duration: Duration) {
        companion object {
            fun fromEnv() =
                Auth(
                    secret = requireEnv("JWT_SECRET"),
                    issuer = getenv("JWT_ISSUER") ?: DEFAULT_ISSUER,
                    duration = (getenv("JWT_DURATION")?.toIntOrNull() ?: DEFAULT_DURATION).days,
                )
        }
    }

    data class Observability(
        val enabled: Boolean,
        val serviceName: String,
        val environment: String,
        val tracingEnabled: Boolean,
        val traceOwner: String,
        val metricsEnabled: Boolean,
        val metricsPath: String,
        val metricsPrometheusEnabled: Boolean,
        val metricsOtlpEnabled: Boolean,
        val otlpEndpoint: String?,
        val otlpProtocol: String,
        val samplingRatio: Double,
        val jsonLogsEnabled: Boolean,
    ) {
        companion object {
            fun fromEnv() =
                Observability(
                    enabled = getenv("OBSERVABILITY_ENABLED")?.toBooleanStrictOrNull() ?: true,
                    serviceName = getenv("OTEL_SERVICE_NAME") ?: "shelf-backend",
                    environment = getenv("OBSERVABILITY_ENVIRONMENT") ?: "development",
                    tracingEnabled =
                        getenv("OBSERVABILITY_TRACING_ENABLED")?.toBooleanStrictOrNull() ?: true,
                    traceOwner = getenv("OBSERVABILITY_TRACE_OWNER") ?: "app",
                    metricsEnabled =
                        getenv("OBSERVABILITY_METRICS_ENABLED")?.toBooleanStrictOrNull() ?: true,
                    metricsPath = getenv("OBSERVABILITY_METRICS_PATH") ?: "/metrics",
                    metricsPrometheusEnabled =
                        getenv("OBSERVABILITY_PROMETHEUS_ENABLED")?.toBooleanStrictOrNull() ?: true,
                    metricsOtlpEnabled =
                        getenv("OBSERVABILITY_OTLP_METRICS_ENABLED")?.toBooleanStrictOrNull()
                            ?: true,
                    otlpEndpoint = getenv("OTEL_EXPORTER_OTLP_ENDPOINT"),
                    otlpProtocol = getenv("OTEL_EXPORTER_OTLP_PROTOCOL") ?: "grpc",
                    samplingRatio =
                        getenv("OBSERVABILITY_TRACE_SAMPLING_RATIO")
                            ?.toDoubleOrNull()
                            ?.coerceIn(0.0, 1.0) ?: 1.0,
                    jsonLogsEnabled =
                        getenv("OBSERVABILITY_JSON_LOGS_ENABLED")?.toBooleanStrictOrNull() ?: false,
                )
        }
    }
}
