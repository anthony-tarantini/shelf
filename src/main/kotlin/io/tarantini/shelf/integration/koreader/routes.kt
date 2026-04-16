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
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.uuid.ExperimentalUuidApi
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
        koreaderTokenAuth(deps.tokenService, deps.userService, deps.observability) {
            logger.info("KOReader users/create accepted")
            call.respond(HttpStatusCode.OK, mapOf("username" to "shelf-user"))
        }
    }
}

private fun Route.registerUsersAuthRoute(deps: Dependencies) {
    get("/users/auth") {
        koreaderTokenAuth(deps.tokenService, deps.userService, deps.observability) {
            logger.info("KOReader users/auth accepted")
            call.respond(HttpStatusCode.OK)
        }
    }
}

private fun Route.registerProgressReadRoute(deps: Dependencies) {
    get("/syncs/progress/{document_id}") {
        koreaderTokenAuth(deps.tokenService, deps.userService, deps.observability) { auth ->
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
        koreaderTokenAuth(deps.tokenService, deps.userService, deps.observability) { auth ->
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
            koreaderTokenAuth(deps.tokenService, deps.userService, deps.observability) { auth ->
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
