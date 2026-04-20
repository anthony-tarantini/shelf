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
import io.tarantini.shelf.integration.koreader.domain.toCommand
import io.tarantini.shelf.integration.koreader.domain.toKoreaderCreateUserCommand
import io.tarantini.shelf.integration.koreader.domain.toKoreaderProgressReadCommand
import io.tarantini.shelf.user.auth.koreaderTokenAuth
import io.tarantini.shelf.user.auth.stripKoreaderDomain
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
        val username = stripKoreaderDomain(request.username.trim())
        val command = toKoreaderCreateUserCommand(username, request.password)
        if (command == null) {
            logger.warn("KOReader users/create rejected reason=empty_username_or_password")
            return@post call.respond(HttpStatusCode.BadRequest)
        }
        handleKoreaderUserCreate(deps, command.username, command.authKey)
    }
}

@Serializable
private data class KoreaderCreateUserRequest(val username: String = "", val password: String = "")

private suspend fun RoutingContext.handleKoreaderUserCreate(
    deps: Dependencies,
    username: String,
    authKey: String,
) {
    either { deps.koreaderAuthService.register(username, authKey) }
        .fold(
            { error ->
                logger.warn("KOReader users/create failed error={}", error::class.simpleName)
                call.respondText(
                    """{"message":"User must register via Shelf first"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Forbidden,
                )
            },
            {
                logger.info("KOReader users/create accepted")
                call.respondText(
                    """{"username":"$username"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Created,
                )
            },
        )
}

private fun Route.registerUsersAuthRoute(deps: Dependencies) {
    get("/users/auth") {
        koreaderTokenAuth(deps.koreaderAuthService, deps.observability) {
            logger.info("KOReader users/auth accepted")
            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun Route.registerProgressReadRoute(deps: Dependencies) {
    get("/syncs/progress/{document_id}") {
        koreaderTokenAuth(deps.koreaderAuthService, deps.observability) { auth ->
            val documentId =
                toKoreaderProgressReadCommand(call.parameters["document_id"])
                    ?: return@koreaderTokenAuth run {
                        logger.warn("KOReader progress read missing document_id")
                        call.respond(HttpStatusCode.BadRequest)
                    }
            either { deps.koreaderSyncService.getProgress(auth.userId, documentId) }
                .fold(
                    {
                        logger.warn("KOReader progress read failed error={}", it::class.simpleName)
                        deps.observability
                            .counter("shelf.koreader.progress_reads", "result", "failure")
                            .increment()
                        call.respond(it.toHttpResponse().first, it.toHttpResponse().second)
                    },
                    { progress ->
                        if (progress == null) {
                            logger.info("KOReader progress read miss")
                            call.respond(HttpStatusCode.NotFound)
                        } else {
                            logger.info("KOReader progress read hit")
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
        koreaderTokenAuth(deps.koreaderAuthService, deps.observability) { auth ->
            val payload =
                runCatching { call.receive<ProgressPayload>() }
                    .getOrElse {
                        logger.warn(
                            "KOReader progress update invalid payload error={}",
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
            val command = payload.toCommand()
            if (command == null) {
                logger.warn("KOReader progress update invalid payload reason=missing_document")
                deps.observability
                    .counter(
                        "shelf.koreader.progress_updates",
                        "result",
                        "failure",
                        "reason",
                        "missing_document",
                    )
                    .increment()
                return@koreaderTokenAuth call.respond(HttpStatusCode.BadRequest)
            }
            either { deps.koreaderSyncService.updateProgress(auth.userId, command) }
                .fold(
                    {
                        logger.warn(
                            "KOReader progress update failed error={}",
                            it::class.simpleName,
                        )
                        deps.observability
                            .counter("shelf.koreader.progress_updates", "result", "failure")
                            .increment()
                        call.respond(it.toHttpResponse().first, it.toHttpResponse().second)
                    },
                    {
                        logger.info("KOReader progress update success")
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
            koreaderTokenAuth(deps.koreaderAuthService, deps.observability) { auth ->
                val userStorageDir =
                    Paths.get(storageRoot, "users", auth.userId.value.toString(), "koreader")
                userStorageDir.createDirectories()
                val statsFile = userStorageDir.resolve("statistics.sqlite")
                logger.info(
                    "KOReader webdav request method={} statsFileExists={}",
                    call.request.local.method.value,
                    statsFile.exists(),
                )

                when (call.request.local.method) {
                    HttpMethod.parse("PROPFIND") -> respondWebdavPropfind(statsFile)
                    HttpMethod.Get -> respondWebdavGet(statsFile)
                    HttpMethod.Put -> respondWebdavPut(statsFile)
                    HttpMethod.Options -> respondWebdavOptions()
                    else -> {
                        logger.warn(
                            "KOReader webdav method not allowed method={}",
                            call.request.local.method.value,
                        )
                        call.respond(HttpStatusCode.MethodNotAllowed)
                    }
                }
            }
        }
    }
}
