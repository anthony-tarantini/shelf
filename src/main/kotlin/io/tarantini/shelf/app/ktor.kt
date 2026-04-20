@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.app

import arrow.core.raise.either
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.xml.xml
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.basic
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.userAgent
import io.ktor.server.resources.Resources
import io.tarantini.shelf.user.identity.domain.LoginUserRequest
import io.tarantini.shelf.user.identity.domain.toCommand
import java.security.MessageDigest
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import nl.adaptivity.xmlutil.serialization.XML
import org.slf4j.event.Level

val kotlinXSerializersModule = SerializersModule {}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this.toByteArray()).joinToString("") {
        "%02x".format(it)
    }

private val noisyHealthPaths = setOf("/readiness", "/health", "/healthz", "/readyz", "/livez")

private fun isNoisyHealthPath(path: String): Boolean = noisyHealthPaths.contains(path)

fun Application.configure(deps: Dependencies) {
    install(DefaultHeaders)
    install(PartialContent) { maxRangeCount = 10 }
    install(Resources)
    if (deps.observability.config.metricsEnabled) {
        install(MicrometerMetrics) { registry = deps.observability.meterRegistry }
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> !isNoisyHealthPath(call.request.path()) }
        mdc("trace_id") { deps.observability.currentTraceId() }
        mdc("span_id") { deps.observability.currentSpanId() }
        mdc("http_method") { it.request.httpMethod.value }
        mdc("http_path") { it.request.path() }
        mdc("remote_host") { it.request.local.remoteHost }
        mdc("x_forwarded_for") { it.request.headers["x-forwarded-for"] ?: "" }
        mdc("x_real_ip") { it.request.headers["x-real-ip"] ?: "" }
        mdc("x_envoy_external_address") { it.request.headers["x-envoy-external-address"] ?: "" }
        mdc("user_agent") { it.request.userAgent() ?: "" }
    }
    install(ContentNegotiation) {
        json(
            Json {
                serializersModule = kotlinXSerializersModule
                isLenient = true
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }
        )
        // Order matters for precedence when client sends multiple or wildcard Accept headers
        xml(XML {}, ContentType.parse("application/atom+xml"))
        xml()
    }
    install(Authentication) {
        basic("opds-auth") {
            realm = "Shelf OPDS"
            validate { credentials ->
                val cacheKey = "${credentials.name}:${credentials.password}".sha256()
                val cached = deps.authCache.get(cacheKey)

                cached
                    ?: either {
                            deps.userService.login(
                                LoginUserRequest(credentials.name, credentials.password).toCommand()
                            )
                        }
                        .fold(
                            { null },
                            { (_, user) ->
                                val principal = UserIdPrincipal(user.id.id.value.toString())
                                deps.authCache.put(cacheKey, principal)
                                principal
                            },
                        )
            }
        }
    }
    install(CORS) {
        deps.env.http.allowedHosts.forEach { allowHost(it) }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Range)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        exposeHeader(HttpHeaders.AcceptRanges)
        exposeHeader(HttpHeaders.ContentRange)
        allowHeader(HttpHeaders.Authorization)
        allowNonSimpleContentTypes = true
    }
}
