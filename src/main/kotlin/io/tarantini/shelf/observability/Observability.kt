package io.tarantini.shelf.observability

import app.cash.sqldelight.Query
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.micrometer.registry.otlp.OtlpConfig
import io.micrometer.registry.otlp.OtlpMeterRegistry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties
import io.opentelemetry.sdk.resources.Resource
import java.time.Duration

class Observability(
    val config: ObservabilityConfig,
    val openTelemetry: OpenTelemetry,
    val meterRegistry: MeterRegistry,
    private val prometheusRegistry: PrometheusMeterRegistry?,
    private val closers: List<() -> Unit>,
) : AutoCloseable {
    fun counter(name: String, vararg tags: String): Counter = meterRegistry.counter(name, *tags)

    fun timer(name: String, vararg tags: String): Timer = meterRegistry.timer(name, *tags)

    fun recordDuration(name: String, vararg tags: String, block: () -> Unit) =
        timer(name, *tags).record(block)

    fun scrapePrometheus(): String? = prometheusRegistry?.scrape()

    fun currentTraceId(): String? =
        Span.current().spanContext.takeIf { it.isValid }?.traceId?.takeUnless { it.isBlank() }

    fun currentSpanId(): String? =
        Span.current().spanContext.takeIf { it.isValid }?.spanId?.takeUnless { it.isBlank() }

    override fun close() {
        closers.reversed().forEach { runCatching { it() } }
    }
}

data class ObservabilityConfig(
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
)

fun ObservabilityConfig.appOwnsServerTraces(): Boolean =
    tracingEnabled && traceOwner.equals("app", ignoreCase = true)

fun observability(config: ObservabilityConfig): Observability {
    if (!config.enabled) {
        val registry = CompositeMeterRegistry()
        return Observability(config, OpenTelemetry.noop(), registry, null, emptyList())
    }

    val openTelemetry =
        if (config.tracingEnabled && config.otlpEndpoint != null) {
            AutoConfiguredOpenTelemetrySdk.builder()
                .addPropertiesSupplier {
                    buildMap {
                        put("otel.service.name", config.serviceName)
                        put("otel.traces.sampler", "parentbased_traceidratio")
                        put("otel.traces.sampler.arg", config.samplingRatio.toString())
                        put("otel.propagators", "tracecontext,baggage")
                        put("otel.exporter.otlp.endpoint", config.otlpEndpoint)
                        put("otel.exporter.otlp.protocol", config.otlpProtocol)
                        put("otel.traces.exporter", "otlp")
                        put("otel.metrics.exporter", "none")
                        put("otel.logs.exporter", "none")
                    }
                }
                .addResourceCustomizer { resource: Resource, _: ConfigProperties ->
                    resource.merge(
                        Resource.builder()
                            .put(AttributeKey.stringKey("service.name"), config.serviceName)
                            .put(
                                AttributeKey.stringKey("deployment.environment"),
                                config.environment,
                            )
                            .build()
                    )
                }
                .setResultAsGlobal()
                .build()
                .openTelemetrySdk
        } else OpenTelemetry.noop()

    val compositeRegistry = CompositeMeterRegistry()
    val closers = mutableListOf<() -> Unit>()

    val prometheusRegistry =
        if (config.metricsEnabled && config.metricsPrometheusEnabled) {
            PrometheusMeterRegistry(PrometheusConfig.DEFAULT).also {
                compositeRegistry.add(it)
                closers.add(it::close)
            }
        } else null

    if (config.metricsEnabled && config.metricsOtlpEnabled && config.otlpEndpoint != null) {
        val otlpRegistry =
            OtlpMeterRegistry(
                object : OtlpConfig {
                    override fun get(key: String): String? = null

                    override fun url(): String = config.otlpEndpoint

                    override fun step(): Duration = Duration.ofSeconds(30)

                    override fun batchSize(): Int = 10_000

                    override fun resourceAttributes(): Map<String, String> =
                        mapOf(
                            "service.name" to config.serviceName,
                            "deployment.environment" to config.environment,
                        )
                },
                io.micrometer.core.instrument.Clock.SYSTEM,
            )
        compositeRegistry.add(otlpRegistry)
        closers.add(otlpRegistry::close)
    }

    if (config.metricsEnabled) {
        ClassLoaderMetrics().bindTo(compositeRegistry)
        JvmGcMetrics().bindTo(compositeRegistry)
        JvmInfoMetrics().bindTo(compositeRegistry)
        JvmMemoryMetrics().bindTo(compositeRegistry)
        JvmThreadMetrics().bindTo(compositeRegistry)
        ProcessorMetrics().bindTo(compositeRegistry)
        UptimeMetrics().bindTo(compositeRegistry)
    }

    return Observability(config, openTelemetry, compositeRegistry, prometheusRegistry, closers)
}

fun Query<*>.gaugeOn(registry: MeterRegistry, name: String, vararg tags: String) {
    Gauge.builder(name, this) { query -> query.executeAsList().size.toDouble() }
        .tags(tags.asList().chunked(2).map { Tag.of(it[0], it[1]) })
        .register(registry)
}
