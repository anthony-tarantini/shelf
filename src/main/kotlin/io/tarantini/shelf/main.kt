package io.tarantini.shelf

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import com.sksamuel.cohort.Cohort
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.Netty
import io.tarantini.shelf.app.Dependencies
import io.tarantini.shelf.app.Env
import io.tarantini.shelf.app.configure
import io.tarantini.shelf.app.dependencies
import io.tarantini.shelf.app.routes
import kotlinx.coroutines.awaitCancellation

fun main() = SuspendApp {
    val env = Env()
    resourceScope {
        val dependencies = dependencies(env)
        server(Netty, host = env.http.host, port = env.http.port) { app(dependencies) }
        awaitCancellation()
    }
}

fun Application.app(module: Dependencies) {
    configure(module)
    routes(module)
    install(Cohort) { healthcheck("/readiness", module.healthCheck) }
}
