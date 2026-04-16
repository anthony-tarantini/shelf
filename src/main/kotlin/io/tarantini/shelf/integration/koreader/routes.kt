@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader

import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.tarantini.shelf.app.Dependencies
import io.tarantini.shelf.app.toHttpResponse
import io.tarantini.shelf.integration.koreader.domain.ProgressPayload
import io.tarantini.shelf.user.auth.koreaderTokenAuth
import io.tarantini.shelf.user.identity.domain.NewUserRequest
import io.tarantini.shelf.user.identity.domain.UserName
import io.tarantini.shelf.user.identity.domain.UserNotFound
import io.tarantini.shelf.user.identity.domain.UsernameAlreadyExists
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

internal val logger = LoggerFactory.getLogger("io.tarantini.shelf.koreader.routes")

fun Route.koreaderRoutes(deps: Dependencies) {
    route("/koreader") {
        configureKoreaderSyncRoutes(deps)
        configureKoreaderWebdavRoutes(deps)
    }
}

private fun Route.configureKoreaderSyncRoutes(deps: Dependencies) {
    route("/sync") {
        registerUsersCreateRoute(deps)
        registerUsersAuthRoute(deps)
        registerProgressReadRoute(deps)
        registerProgressUpdateRoute(deps)
    }
}

private fun Route.registerUsersCreateRoute(deps: Dependencies) {
    post("/users/create") {
        val request =
            runCatching { call.receive<KoreaderCreateUserRequest>() }
                .getOrElse {
                    logger.warn("KOReader users/create rejected reason=invalid_payload")
                    return@post call.respond(HttpStatusCode.BadRequest)
                }
        val username = request.username.trim()
        val password = request.password.trim()
        if (username.isEmpty() || password.isEmpty()) {
            logger.warn("KOReader users/create rejected reason=empty_username_or_password")
            return@post call.respond(HttpStatusCode.BadRequest)
        }
        handleKoreaderUserCreate(deps, username, password)
    }
}

@Serializable
private data class KoreaderCreateUserRequest(val username: String = "", val password: String = "")

private suspend fun RoutingContext.handleKoreaderUserCreate(
    deps: Dependencies,
    username: String,
    password: String,
) {
    val existingCheck = either { deps.userService.getUserByName(UserName(username)) }
    if (existingCheck.isRight()) {
        logger.info("KOReader users/create rejected reason=username_exists username={}", username)
        return call.respond(HttpStatusCode.PaymentRequired)
    }
    if (existingCheck.swap().getOrNull() != UserNotFound) {
        logger.warn(
            "KOReader users/create failed username={} error={}",
            username,
            existingCheck.swap().getOrNull()?.let { it::class.simpleName } ?: "unknown",
        )
        return call.respond(HttpStatusCode.BadRequest)
    }

    either {
            deps.userService.register(
                NewUserRequest(
                    email = "$username@koreader.local",
                    username = username,
                    password = password,
                )
            )
        }
        .fold(
            { error ->
                if (error == UsernameAlreadyExists) {
                    logger.info(
                        "KOReader users/create rejected reason=username_exists username={}",
                        username,
                    )
                    call.respond(HttpStatusCode.PaymentRequired)
                } else {
                    logger.warn(
                        "KOReader users/create failed username={} error={}",
                        username,
                        error::class.simpleName,
                    )
                    call.respond(HttpStatusCode.BadRequest)
                }
            },
            {
                logger.info("KOReader users/create accepted username={}", username)
                call.respond(HttpStatusCode.Created, mapOf("username" to username))
            },
        )
}

private fun Route.registerUsersAuthRoute(deps: Dependencies) {
    get("/users/auth") {
        koreaderTokenAuth(deps.userService, deps.observability) {
            logger.info("KOReader users/auth accepted")
            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun Route.registerProgressReadRoute(deps: Dependencies) {
    get("/syncs/progress/{document_id}") {
        koreaderTokenAuth(deps.userService, deps.observability) { auth ->
            val documentId =
                call.parameters["document_id"]
                    ?: return@koreaderTokenAuth run {
                        logger.warn("KOReader progress read missing document_id")
                        call.respond(HttpStatusCode.BadRequest)
                    }
            either { deps.koreaderSyncService.getProgress(auth.userId, documentId) }
                .fold(
                    {
                        logger.warn(
                            "KOReader progress read failed userId={} documentId={} error={}",
                            auth.userId.value,
                            documentId,
                            it::class.simpleName,
                        )
                        deps.observability
                            .counter("shelf.koreader.progress_reads", "result", "failure")
                            .increment()
                        call.respond(it.toHttpResponse().first, it.toHttpResponse().second)
                    },
                    { progress ->
                        if (progress == null) {
                            logger.info(
                                "KOReader progress read miss userId={} documentId={}",
                                auth.userId.value,
                                documentId,
                            )
                            call.respond(HttpStatusCode.NotFound)
                        } else {
                            logger.info(
                                "KOReader progress read hit userId={} documentId={}",
                                auth.userId.value,
                                documentId,
                            )
                            deps.observability
                                .counter("shelf.koreader.progress_reads", "result", "success")
                                .increment()
                            call.respond(progress.progressData)
                        }
                    },
                )
        }
    }
}

private fun Route.registerProgressUpdateRoute(deps: Dependencies) {
    put("/syncs/progress") {
        koreaderTokenAuth(deps.userService, deps.observability) { auth ->
            val payload =
                runCatching { call.receive<ProgressPayload>() }
                    .getOrElse {
                        logger.warn(
                            "KOReader progress update invalid payload userId={} error={}",
                            auth.userId.value,
                            it::class.simpleName,
                        )
                        deps.observability
                            .counter(
                                "shelf.koreader.progress_updates",
                                "result",
                                "failure",
                                "reason",
                                "invalid_payload",
                            )
                            .increment()
                        return@koreaderTokenAuth call.respond(HttpStatusCode.BadRequest)
                    }
            either { deps.koreaderSyncService.updateProgress(auth.userId, payload) }
                .fold(
                    {
                        logger.warn(
                            "KOReader progress update failed userId={} document={} error={}",
                            auth.userId.value,
                            payload.document,
                            it::class.simpleName,
                        )
                        deps.observability
                            .counter("shelf.koreader.progress_updates", "result", "failure")
                            .increment()
                        call.respond(it.toHttpResponse().first, it.toHttpResponse().second)
                    },
                    {
                        logger.info(
                            "KOReader progress update success userId={} document={}",
                            auth.userId.value,
                            payload.document,
                        )
                        deps.observability
                            .counter("shelf.koreader.progress_updates", "result", "success")
                            .increment()
                        call.respond(HttpStatusCode.OK)
                    },
                )
        }
    }
}

private fun Route.configureKoreaderWebdavRoutes(deps: Dependencies) {
    route("/webdav") {
        val storageRoot = deps.env.storage.path
        handle {
            koreaderTokenAuth(deps.userService, deps.observability) { auth ->
                val userStorageDir =
                    Paths.get(storageRoot, "users", auth.userId.value.toString(), "koreader")
                userStorageDir.createDirectories()
                val statsFile = userStorageDir.resolve("statistics.sqlite")
                logger.info(
                    "KOReader webdav request method={} uri={} userId={} statsFileExists={}",
                    call.request.local.method.value,
                    call.request.uri,
                    auth.userId.value,
                    statsFile.exists(),
                )

                when (call.request.local.method) {
                    HttpMethod.parse("PROPFIND") -> respondWebdavPropfind(statsFile)
                    HttpMethod.Get -> respondWebdavGet(statsFile)
                    HttpMethod.Put -> respondWebdavPut(statsFile)
                    HttpMethod.Options -> respondWebdavOptions()
                    else -> {
                        logger.warn(
                            "KOReader webdav method not allowed method={} uri={}",
                            call.request.local.method.value,
                            call.request.uri,
                        )
                        call.respond(HttpStatusCode.MethodNotAllowed)
                    }
                }
            }
        }
    }
}
