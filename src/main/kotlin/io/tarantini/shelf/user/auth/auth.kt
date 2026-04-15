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
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.AccessDenied
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.observability.Observability
import io.tarantini.shelf.user.identity.TokenService
import io.tarantini.shelf.user.identity.UserService
import io.tarantini.shelf.user.identity.domain.JwtMissing
import io.tarantini.shelf.user.identity.domain.LoginUserRequest
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@JvmInline value class JwtToken(val value: String)

data class JwtContext(val token: JwtToken, val userId: UserId)

data class KoreaderContext(val userId: UserId)

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
    tokenService: TokenService,
    userService: UserService,
    observability: Observability,
    crossinline body: suspend RoutingContext.(KoreaderContext) -> Unit,
) {
    either {
            val username = ensureNotNull(call.request.headers["x-auth-user"]) { AccessDenied }
            val token = ensureNotNull(call.request.headers["x-auth-key"]) { AccessDenied }
            val userId = ensureNotNull(tokenService.validateToken(token)) { AccessDenied }
            val user = userService.getUserById(userId)
            ensure(user.username.value == username) { AccessDenied }
            KoreaderContext(userId)
        }
        .fold(
            {
                observability.counter("shelf.koreader.auth", "result", "failure").increment()
                call.respond(HttpStatusCode.Unauthorized)
            },
            { context ->
                observability.counter("shelf.koreader.auth", "result", "success").increment()
                body(this, context)
            },
        )
}

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
