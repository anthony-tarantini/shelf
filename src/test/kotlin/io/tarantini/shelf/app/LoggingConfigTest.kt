package io.tarantini.shelf.app

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LoggingConfigTest :
    StringSpec({
        "configure logging enables JSON output when observability flag is true" {
            val previous = System.getProperty(LOG_JSON_ENABLED_PROPERTY)
            try {
                configureLogging(observability(jsonLogsEnabled = true))
                System.getProperty(LOG_JSON_ENABLED_PROPERTY) shouldBe "true"
            } finally {
                restoreProperty(previous)
            }
        }

        "configure logging keeps line output when observability flag is false" {
            val previous = System.getProperty(LOG_JSON_ENABLED_PROPERTY)
            try {
                configureLogging(observability(jsonLogsEnabled = false))
                System.getProperty(LOG_JSON_ENABLED_PROPERTY) shouldBe "false"
            } finally {
                restoreProperty(previous)
            }
        }
    })

private fun observability(jsonLogsEnabled: Boolean): Env.Observability =
    Env.Observability(
        enabled = true,
        serviceName = "shelf-test",
        environment = "test",
        tracingEnabled = false,
        traceOwner = "app",
        metricsEnabled = true,
        metricsPath = "/metrics",
        metricsPrometheusEnabled = true,
        metricsOtlpEnabled = false,
        otlpEndpoint = null,
        otlpProtocol = "grpc",
        samplingRatio = 1.0,
        jsonLogsEnabled = jsonLogsEnabled,
    )

private fun restoreProperty(previous: String?) {
    if (previous == null) {
        System.clearProperty(LOG_JSON_ENABLED_PROPERTY)
    } else {
        System.setProperty(LOG_JSON_ENABLED_PROPERTY, previous)
    }
}
