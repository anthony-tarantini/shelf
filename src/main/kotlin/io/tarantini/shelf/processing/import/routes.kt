@file:OptIn(ExperimentalSerializationApi::class)

package io.tarantini.shelf.processing.import

import app.cash.sqldelight.Transacter
import arrow.core.raise.context.either
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.patch
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.Response
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.book.respondEither
import io.tarantini.shelf.processing.import.domain.*
import io.tarantini.shelf.processing.import.staging.StagedBookService
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.adminAuth
import io.tarantini.shelf.user.auth.jwtAuth
import io.tarantini.shelf.user.identity.UserService
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.ExperimentalSerializationApi

fun Route.importRoutes(
    importService: ImportService,
    jwtService: JwtService,
    userService: UserService,
) {
    post<ImportResource> { _ ->
        jwtAuth(jwtService) { context ->
            val multipart = call.receiveMultipart()
            var tempFile: Path? = null
            var fileName: String? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileName = part.originalFileName
                        val suffix = fileName?.let { ".${it.substringAfterLast(".")}" } ?: ".tmp"
                        val p = Files.createTempFile("shelf-import-", suffix)
                        part.provider().toInputStream().use { input ->
                            p.toFile().outputStream().use { output -> input.copyTo(output) }
                        }
                        tempFile = p
                    }

                    else -> {}
                }
                part.dispose()
            }

            respond(
                {
                    val path = ensureNotNull(tempFile) { MissingFile }
                    val name = fileName ?: "unknown.epub"
                    // All imports are staged now
                    with(context) { importService.queueImport(path, name, deleteSource = true) }
                    mapOf("message" to "Import queued")
                },
                HttpStatusCode.Created,
            )
        }
    }

    post<ImportResource.Scan> { resource ->
        adminAuth(jwtService, userService) { context ->
            val req = call.receive<Request<ScanDirectoryRequest>>().data
            respond(
                {
                    // req.staged is ignored, we always stage
                    with(context) { importService.scanDirectory(req.path) }
                    mapOf("message" to "Scan started")
                },
                HttpStatusCode.Accepted,
            )
        }
    }

    get<ImportResource.Progress> {
        jwtAuth(jwtService) { context ->
            with(context) { either { importService.getScanProgress() } }
                .fold(
                    ifLeft = { error -> respond(error) },
                    ifRight = { progress -> call.respond(Response(progress)) },
                )
        }
    }
}

context(transactor: Transacter)
fun Route.stagedRoutes(
    jwtService: JwtService,
    stagedBookService: StagedBookService,
    storageService: StorageService,
) {
    suspend fun RoutingContext.respondStagedCover(path: StoragePath) {
        respondEither {
            val (length, channel) = storageService.getReadChannel(path)
            object : OutgoingContent.ReadChannelContent() {
                override val contentLength = length
                override val contentType =
                    when (path.extension().lowercase()) {
                        "png" -> ContentType.Image.PNG
                        "webp" -> ContentType.parse("image/webp")
                        else -> ContentType.Image.JPEG
                    }

                override fun readFrom() = channel
            }
        }
    }

    get<StagedResource> { params ->
        jwtAuth(jwtService) { context ->
            with(context) {
                respond({
                    stagedBookService.getAll(
                        page = params.page,
                        size = params.size,
                        sortBy = params.sortBy,
                        sortDir = params.sortDir,
                        author = params.author,
                    )
                })
            }
        }
    }

    get<StagedResource.Id.Cover> { params ->
        jwtAuth(jwtService) { context ->
            with(context) {
                either {
                        val stagedBook = stagedBookService.getById(params.id)
                        val coverPath =
                            stagedBook.coverPath?.let { StoragePath(it) }
                                ?: raise(HttpStatusCode.NotFound)
                        if (
                            either { storageService.exists(coverPath.thumbnail()) }
                                .fold({ false }, { it })
                        ) {
                            coverPath.thumbnail()
                        } else {
                            coverPath
                        }
                    }
                    .fold(
                        ifLeft = { err ->
                            when (err) {
                                is HttpStatusCode -> call.respond(err)
                                is io.tarantini.shelf.app.AppError -> respond(err)
                                else -> call.respond(HttpStatusCode.InternalServerError)
                            }
                        },
                        ifRight = { respondStagedCover(it) },
                    )
            }
        }
    }

    post<StagedResource.Batch> {
        jwtAuth(jwtService) { context ->
            val req = call.receive<Request<StagedBatchRequest>>().data
            with(context) { respond({ stagedBookService.batch(req) }, HttpStatusCode.Accepted) }
        }
    }

    get<StagedResource.Batch.Progress> {
        jwtAuth(jwtService) { context ->
            with(context) { either { stagedBookService.getBatchProgress() } }
                .fold(
                    ifLeft = { error -> respond(error) },
                    ifRight = { progress -> call.respond(Response(progress)) },
                )
        }
    }

    delete<StagedResource.Id> { params ->
        jwtAuth(jwtService) { context ->
            with(context) {
                respond({ stagedBookService.delete(params.id) }, HttpStatusCode.NoContent)
            }
        }
    }

    post<StagedResource.Id.Promote> { params ->
        jwtAuth(jwtService) { context ->
            with(context) { respond({ stagedBookService.promote(params.id) }) }
        }
    }

    post<StagedResource.Id.Merge> { params ->
        jwtAuth(jwtService) { context ->
            val req = call.receive<Request<MergeStagedBookRequest>>().data
            with(context) { respond({ stagedBookService.merge(params.id, req.targetBookId) }) }
        }
    }

    patch<StagedResource.Id.Update> { params ->
        jwtAuth(jwtService) { context ->
            respond({
                val req = call.receive<Request<UpdateStagedBookRequest>>().data
                with(context) {
                    stagedBookService.update(
                        stagedId = params.id,
                        title = req.title,
                        description = req.description,
                        authors = req.authors,
                        selectedAuthorIds = req.selectedAuthorIds,
                        publisher = req.publisher,
                        publishYear = req.publishYear,
                        genres = req.genres,
                        moods = req.moods,
                        series = req.series,
                        ebookMetadata = req.ebookMetadata,
                        audiobookMetadata = req.audiobookMetadata,
                        coverUrl = req.coverUrl,
                    )
                }
            })
        }
    }
}
