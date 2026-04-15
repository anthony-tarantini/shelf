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
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.uuid.ExperimentalUuidApi

fun Route.koreaderRoutes(deps: Dependencies) {
    route("/koreader") {
        // Progress Sync API
        route("/sync") {
            post("/users/create") {
                koreaderTokenAuth(deps.tokenService, deps.userService, deps.observability) {
                    call.respond(HttpStatusCode.OK, mapOf("username" to "shelf-user"))
                }
            }

            get("/users/auth") {
                koreaderTokenAuth(deps.tokenService, deps.userService, deps.observability) {
                    call.respond(HttpStatusCode.OK)
                }
            }

            get("/syncs/progress/{document_id}") {
                koreaderTokenAuth(deps.tokenService, deps.userService, deps.observability) { auth ->
                    val documentId =
                        call.parameters["document_id"]
                            ?: return@koreaderTokenAuth call.respond(HttpStatusCode.BadRequest)

                    either { deps.koreaderSyncService.getProgress(auth.userId, documentId) }
                        .fold(
                            {
                                deps.observability
                                    .counter("shelf.koreader.progress_reads", "result", "failure")
                                    .increment()
                                call.respond(it.toHttpResponse().first, it.toHttpResponse().second)
                            },
                            { progress ->
                                if (progress == null) {
                                    call.respond(HttpStatusCode.NotFound)
                                } else {
                                    deps.observability
                                        .counter(
                                            "shelf.koreader.progress_reads",
                                            "result",
                                            "success",
                                        )
                                        .increment()
                                    call.respond(progress.progressData)
                                }
                            },
                        )
                }
            }

            put("/syncs/progress") {
                koreaderTokenAuth(deps.tokenService, deps.userService, deps.observability) { auth ->
                    val payload = call.receive<ProgressPayload>()

                    either { deps.koreaderSyncService.updateProgress(auth.userId, payload) }
                        .fold(
                            {
                                deps.observability
                                    .counter("shelf.koreader.progress_updates", "result", "failure")
                                    .increment()
                                call.respond(it.toHttpResponse().first, it.toHttpResponse().second)
                            },
                            {
                                deps.observability
                                    .counter("shelf.koreader.progress_updates", "result", "success")
                                    .increment()
                                call.respond(HttpStatusCode.OK)
                            },
                        )
                }
            }
        }

        // Minimal WebDAV for Stats Sync
        route("/webdav") {
            val storageRoot = deps.env.storage.path

            handle {
                koreaderTokenAuth(deps.tokenService, deps.userService, deps.observability) { auth ->
                    val userStorageDir =
                        Paths.get(storageRoot, "users", auth.userId.value.toString(), "koreader")
                    userStorageDir.createDirectories()
                    val statsFile = userStorageDir.resolve("statistics.sqlite")

                    when (call.request.local.method) {
                        HttpMethod.parse("PROPFIND") -> {
                            val xml =
                                """
                                <?xml version="1.0" encoding="utf-8" ?>
                                <D:multistatus xmlns:D="DAV:">
                                    <D:response>
                                        <D:href>${call.request.uri}</D:href>
                                        <D:propstat>
                                            <D:prop>
                                                <D:displayname>statistics.sqlite</D:displayname>
                                                <D:getcontentlength>${if (statsFile.exists()) statsFile.toFile().length() else 0}</D:getcontentlength>
                                                <D:resourcetype/>
                                            </D:prop>
                                            <D:status>HTTP/1.1 200 OK</D:status>
                                        </D:propstat>
                                    </D:response>
                                </D:multistatus>
                            """
                                    .trimIndent()
                            call.respondText(
                                xml,
                                ContentType.parse("application/xml"),
                                HttpStatusCode.MultiStatus,
                            )
                        }
                        HttpMethod.Get -> {
                            if (statsFile.exists()) {
                                call.respondOutputStream(
                                    ContentType.parse("application/x-sqlite3")
                                ) {
                                    statsFile.inputStream().use { it.copyTo(this) }
                                }
                            } else {
                                call.respond(HttpStatusCode.NotFound)
                            }
                        }
                        HttpMethod.Put -> {
                            call.receiveStream().use { input ->
                                statsFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            call.respond(HttpStatusCode.Created)
                        }
                        HttpMethod.Options -> {
                            call.response.header("Allow", "GET, PUT, PROPFIND, OPTIONS")
                            call.response.header("DAV", "1")
                            call.respond(HttpStatusCode.OK)
                        }
                        else -> call.respond(HttpStatusCode.MethodNotAllowed)
                    }
                }
            }
        }
    }
}
