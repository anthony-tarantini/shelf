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
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.AccessDenied
import io.tarantini.shelf.app.id
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.observability.Observability
import io.tarantini.shelf.user.identity.UserService
import io.tarantini.shelf.user.identity.domain.JwtMissing
import io.tarantini.shelf.user.identity.domain.LoginUserRequest
import io.tarantini.shelf.user.identity.domain.UserId
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
    userService: UserService,
    observability: Observability,
    crossinline body: suspend RoutingContext.(KoreaderContext) -> Unit,
) {
    either {
            val username = ensureNotNull(call.request.headers["x-auth-user"]) { AccessDenied }
            val token = ensureNotNull(call.request.headers["x-auth-key"]) { AccessDenied }.trim()
            val userId =
                ensureNotNull(resolveKoreaderUserId(userService, token, username.trim())) {
                    AccessDenied
                }
            val user = userService.getUserById(userId)
            ensure(user.username.value == username.trim()) { AccessDenied }
            KoreaderContext(userId)
        }
        .fold(
            {
                val failureReason =
                    when {
                        call.request.headers["x-auth-user"].isNullOrBlank() -> "missing_user_header"
                        call.request.headers["x-auth-key"].isNullOrBlank() -> "missing_key_header"
                        else -> "invalid_credentials"
                    }
                logger.warn(
                    "KOReader auth failed method={} uri={} reason={} username={}",
                    call.request.httpMethod.value,
                    call.request.uri,
                    failureReason,
                    call.request.headers["x-auth-user"] ?: "<missing>",
                )
                observability
                    .counter("shelf.koreader.auth", "result", "failure", "reason", failureReason)
                    .increment()
                call.respond(HttpStatusCode.Unauthorized)
            },
            { context ->
                logger.info(
                    "KOReader auth succeeded method={} uri={} userId={}",
                    call.request.httpMethod.value,
                    call.request.uri,
                    context.userId.value,
                )
                observability.counter("shelf.koreader.auth", "result", "success").increment()
                body(this, context)
            },
        )
}

@OptIn(ExperimentalEncodingApi::class)
context(_: RaiseContext)
suspend fun resolveKoreaderUserId(
    userService: UserService,
    rawToken: String,
    username: String,
): UserId? {
    val normalized = rawToken.trim()
    if (normalized.isEmpty()) return null
    val loginResult = either {
        userService.login(LoginUserRequest(koreaderEmail(username), normalized))
    }
    return loginResult.fold({ null }, { (_, user) -> user.id.id })
}

private fun koreaderEmail(username: String): String = "$username@koreader.local"

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
                    userService.login(LoginUserRequest(username, password))
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
