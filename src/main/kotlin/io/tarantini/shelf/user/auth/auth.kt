@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.user.auth

import arrow.core.raise.context.either
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.AccessDenied
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.integration.koreader.KoreaderAuthService
import io.tarantini.shelf.observability.Observability
import io.tarantini.shelf.user.identity.UserService
import io.tarantini.shelf.user.identity.domain.JwtMissing
import io.tarantini.shelf.user.identity.domain.LoginUserRequest
import io.tarantini.shelf.user.identity.domain.UserId
import io.tarantini.shelf.user.identity.domain.toCommand
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import org.slf4j.LoggerFactory

@JvmInline value class JwtToken(val value: String)

data class JwtContext(val token: JwtToken, val userId: UserId)

data class KoreaderContext(val userId: UserId)

val logger = LoggerFactory.getLogger("io.tarantini.shelf.koreader.auth")

suspend inline fun RoutingContext.jwtAuth(
    jwtService: JwtService,
    crossinline body: suspend RoutingContext.(JwtContext) -> Unit,
) {
    either {
            val token = jwtToken()
            token to jwtService.verifyJwtToken(JwtToken(token))
        }
        .fold(
            { error -> respond(error) },
            { (token, userId) -> body(this, JwtContext(JwtToken(token), userId)) },
        )
}

suspend inline fun RoutingContext.adminAuth(
    jwtService: JwtService,
    userService: UserService,
    crossinline body: suspend RoutingContext.(JwtContext) -> Unit,
) {
    jwtAuth(jwtService) { context ->
        either {
                val user = userService.getUserById(context.userId)
                requireAdmin(user.role)
            }
            .fold({ error -> respond(error) }, { body(this, context) })
    }
}

suspend inline fun RoutingContext.sharedCatalogRead(
    jwtService: JwtService,
    crossinline body: suspend RoutingContext.(JwtContext) -> Unit,
) {
    jwtAuth(jwtService) { context ->
        either { with(context) { requireAuthenticatedSharedAccess() } }
            .fold({ error -> respond(error) }, { body(this, context) })
    }
}

suspend inline fun RoutingContext.sharedCatalogMutation(
    jwtService: JwtService,
    crossinline body: suspend RoutingContext.(JwtContext) -> Unit,
) {
    jwtAuth(jwtService) { context ->
        either { with(context) { requireAuthenticatedSharedMutation() } }
            .fold({ error -> respond(error) }, { body(this, context) })
    }
}

suspend inline fun RoutingContext.koreaderTokenAuth(
    koreaderAuthService: KoreaderAuthService,
    observability: Observability,
    crossinline body: suspend RoutingContext.(KoreaderContext) -> Unit,
) {
    val rawUsername = call.request.headers["x-auth-user"]
    val rawKey = call.request.headers["x-auth-key"]

    if (rawUsername.isNullOrBlank() || rawKey.isNullOrBlank()) {
        val reason =
            if (rawUsername.isNullOrBlank()) "missing_user_header" else "missing_key_header"
        logger.warn(
            "KOReader auth failed method={} reason={}",
            call.request.httpMethod.value,
            reason,
        )
        observability
            .counter("shelf.koreader.auth", "result", "failure", "reason", reason)
            .increment()
        call.respond(HttpStatusCode.Unauthorized)
        return
    }

    val username = stripKoreaderDomain(rawUsername.trim())
    val authKey = rawKey.trim()
    val userId = either { koreaderAuthService.authenticate(username, authKey) }.getOrNull()

    if (userId == null) {
        logger.warn(
            "KOReader auth failed method={} reason=invalid_credentials",
            call.request.httpMethod.value,
        )
        observability
            .counter("shelf.koreader.auth", "result", "failure", "reason", "invalid_credentials")
            .increment()
        call.respond(HttpStatusCode.Unauthorized)
        return
    }

    logger.info("KOReader auth succeeded method={}", call.request.httpMethod.value)
    observability.counter("shelf.koreader.auth", "result", "success").increment()
    body(this, KoreaderContext(userId))
}

fun stripKoreaderDomain(username: String): String = username.removeSuffix("@koreader.local")

fun Route.sharedCatalogFeedAuth(build: Route.() -> Unit) {
    authenticate("opds-auth", build = build)
}

@OptIn(ExperimentalEncodingApi::class)
suspend inline fun RoutingContext.sharedCatalogAssetRead(
    jwtService: JwtService,
    userService: UserService,
    observability: Observability,
    crossinline body: suspend RoutingContext.() -> Unit,
) {
    either {
            val authorization =
                ensureNotNull(call.request.headers[HttpHeaders.Authorization]) { AccessDenied }

            when {
                authorization.startsWith("Bearer ", ignoreCase = true) -> {
                    val token = authorization.substringAfter(' ').trim()
                    val context =
                        JwtContext(JwtToken(token), jwtService.verifyJwtToken(JwtToken(token)))
                    with(context) { requireAuthenticatedSharedAccess() }
                }
                authorization.startsWith("Basic ", ignoreCase = true) -> {
                    val encoded = authorization.substringAfter(' ').trim()
                    val decoded =
                        runCatching { Base64.Default.decode(encoded).decodeToString() }
                            .getOrElse { raise(AccessDenied) }
                    val separator = decoded.indexOf(':')
                    ensure(separator >= 0) { AccessDenied }
                    val username = decoded.substring(0, separator)
                    val password = decoded.substring(separator + 1)
                    userService.login(LoginUserRequest(username, password).toCommand())
                    observability
                        .counter("shelf.catalog.asset_auth", "scheme", "basic", "result", "success")
                        .increment()
                }
                else -> raise(AccessDenied)
            }
        }
        .fold(
            {
                observability.counter("shelf.catalog.asset_auth", "result", "failure").increment()
                call.respond(HttpStatusCode.Unauthorized)
            },
            { body(this) },
        )
}

context(_: RaiseContext)
fun RoutingContext.jwtToken(): String =
    ensureNotNull((call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)?.blob) {
        JwtMissing
    }
