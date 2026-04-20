package io.tarantini.shelf.observability

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.observabilityRoutes(observability: Observability) {
    if (!observability.config.metricsEnabled || !observability.config.metricsPrometheusEnabled) {
        return
    }

    get(observability.config.metricsPath) {
        val body = observability.scrapePrometheus() ?: ""
        call.respondText(body, ContentType.parse("text/plain; version=0.0.4; charset=utf-8"))
    }
}
