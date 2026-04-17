@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import arrow.core.raise.either
import arrow.fx.coroutines.resourceScope
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.put
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.tarantini.shelf.app.AppError
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.author.AuthorService
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.ebookMimeType
import io.tarantini.shelf.catalog.series.SeriesService
import io.tarantini.shelf.observability.Observability
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.user.activity.ActivityService
import io.tarantini.shelf.user.activity.domain.ReadStatusRequest
import io.tarantini.shelf.user.activity.domain.ReadingProgress
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.jwtAuth
import io.tarantini.shelf.user.auth.sharedCatalogAssetRead
import io.tarantini.shelf.user.auth.sharedCatalogMutation
import io.tarantini.shelf.user.auth.sharedCatalogRead
import io.tarantini.shelf.user.identity.UserService
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.ExperimentalSerializationApi

fun Route.bookRoutes(
    bookService: BookService,
    storageService: StorageService,
    authorService: AuthorService,
    seriesService: SeriesService,
    activityService: ActivityService,
    jwtService: JwtService,
    userService: UserService,
    observability: Observability,
) {
    suspend fun RoutingContext.respondCover(path: StoragePath) {
        respondEither {
            val (length, channel) = storageService.getReadChannel(path)
            object : OutgoingContent.ReadChannelContent() {
                override val contentLength = length
                override val contentType =
                    ContentType.defaultForFileExtension(path.extension()).let {
                        if (it == ContentType.Application.OctetStream) ContentType.Image.JPEG
                        else it
                    }

                override fun readFrom() = channel
            }
        }
    }

    get<BooksResource> {
        sharedCatalogRead(jwtService) { auth ->
            with(auth) {
                respond({ activityService.enrichBookSummaries(bookService.getBookSummaries()) })
            }
        }
    }
    get<BooksResource.Details> {
        sharedCatalogRead(jwtService) { auth ->
            with(auth) {
                respond({ activityService.enrichBookAggregates(bookService.getBooksAggregate()) })
            }
        }
    }

    get<BooksResource.Page> { resource ->
        sharedCatalogRead(jwtService) { auth ->
            with(auth) {
                respond({
                    val page =
                        bookService.getBooksPage(
                            page = resource.page,
                            size = resource.size,
                            sortBy = resource.sortBy,
                            sortDir = resource.sortDir,
                        )
                    page.copy(items = activityService.enrichBookAggregates(page.items))
                })
            }
        }
    }

    get<BooksResource.Id> { resource ->
        sharedCatalogRead(jwtService) { respond({ bookService.getBook(BookId(resource.id)) }) }
    }

    get<BooksResource.Id.Details> { resource ->
        sharedCatalogRead(jwtService) { auth ->
            with(auth) {
                respond({
                    activityService.enrichBookAggregate(
                        bookService.getBookAggregate(BookId(resource.id))
                    )
                })
            }
        }
    }

    get<BooksResource.Id.Authors> { resource ->
        sharedCatalogRead(jwtService) {
            respond({
                val bookId = BookId(resource.id)
                authorService.getAuthorsForBooks(listOf(bookId)).getOrDefault(bookId, emptyList())
            })
        }
    }

    get<BooksResource.Id.Series> { resource ->
        sharedCatalogRead(jwtService) {
            respond({
                val bookId = BookId(resource.id)
                seriesService.getSeriesForBooks(listOf(bookId)).getOrDefault(bookId, emptyList())
            })
        }
    }

    delete<BooksResource.Id> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({ bookService.deleteBook(BookId(resource.id)) })
        }
    }

    get<BooksResource.Id.Stream> { resource ->
        sharedCatalogRead(jwtService) {
            respondEither {
                val bookId = BookId(resource.id)
                val edition = either { bookService.getPrimaryEdition(bookId) }.bind()
                val (length, channel) = storageService.getReadChannel(edition.path)

                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength = length
                    override val contentType = ContentType.Audio.MPEG

                    override fun readFrom() = channel
                }
            }
        }
    }

    get<BooksResource.Id.Download> { resource ->
        sharedCatalogAssetRead(jwtService, userService, observability) {
            respondEither {
                val bookId = BookId(resource.id)
                val aggregate = either { bookService.getBookAggregate(bookId) }.bind()
                val ebook = either { bookService.getEbookEdition(bookId) }.bind()

                val (length, channel) = storageService.getReadChannel(ebook.path)
                observability.counter("shelf.book_downloads", "result", "success").increment()

                val extension = ebook.path.value.substringAfterLast('.', "epub")
                val downloadContentType = ContentType.parse(ebookMimeType(ebook.path.value))

                this@get.call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                            ContentDisposition.Parameters.FileName,
                            "${aggregate.book.title}.$extension",
                        )
                        .toString(),
                )

                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength = length
                    override val contentType = downloadContentType

                    override fun readFrom() = channel
                }
            }
        }
    }

    get<BooksResource.Id.Cover> { resource ->
        sharedCatalogAssetRead(jwtService, userService, observability) {
            either { bookService.getPreferredCoverPath(BookId(resource.id)) }
                .fold(ifLeft = { err: AppError -> respond(err) }, ifRight = { respondCover(it) })
        }
    }

    get<BooksResource.Id.Thumbnail> { resource ->
        sharedCatalogAssetRead(jwtService, userService, observability) {
            either { bookService.getThumbnailPath(BookId(resource.id)) }
                .fold(ifLeft = { err: AppError -> respond(err) }, ifRight = { respondCover(it) })
        }
    }

    get<BooksResource.Id.Epub> { resource ->
        sharedCatalogRead(jwtService) {
            resourceScope {
                respondEither {
                    val bookId = BookId(resource.id)
                    val ebook = either { bookService.getEbookEdition(bookId) }.bind()

                    val entryPath = resource.path.joinToString("/")
                    val (length, channel) =
                        storageService.getZipEntryReadChannel(
                            this@resourceScope,
                            ebook.path,
                            entryPath,
                        ) ?: raise(HttpStatusCode.NotFound)

                    object : OutgoingContent.ReadChannelContent() {
                        override val contentLength = length
                        override val contentType =
                            ContentType.defaultForFileExtension(
                                entryPath.substringAfterLast('.', "")
                            )

                        override fun readFrom() = channel
                    }
                }
            }
        }
    }

    get<BooksResource.Id.Progress> { resource ->
        jwtAuth(jwtService) { auth ->
            with(auth) {
                respond({
                    activityService.getProgress(userId, BookId(resource.id)) ?: ReadingProgress()
                })
            }
        }
    }

    get<BooksResource.Id.Status> { resource ->
        jwtAuth(jwtService) { auth ->
            with(auth) { respond({ activityService.getReadStatus(BookId(resource.id)) }) }
        }
    }

    put<BooksResource.Id.Status> { resource ->
        jwtAuth(jwtService) { auth ->
            val req = call.receive<Request<ReadStatusRequest>>().data
            with(auth) {
                respond({
                    activityService.saveReadStatus(BookId(resource.id), req.status)
                    mapOf("message" to "Read status saved")
                })
            }
        }
    }

    put<BooksResource.Id.Progress> { resource ->
        jwtAuth(jwtService) { auth ->
            val req = call.receive<Request<ReadingProgress>>().data
            with(auth) {
                respond({
                    activityService.saveProgress(userId, BookId(resource.id), req)
                    mapOf("message" to "Progress saved")
                })
            }
        }
    }
}

suspend inline fun RoutingContext.respondEither(
    crossinline block: suspend arrow.core.raise.Raise<Any>.() -> Any
) {
    either { block() }
        .fold(
            ifLeft = { err ->
                when (err) {
                    is HttpStatusCode -> this@respondEither.call.respond(err)
                    is AppError -> respond(err)
                    else -> this@respondEither.call.respond(HttpStatusCode.InternalServerError)
                }
            },
            ifRight = {
                if (it is OutgoingContent) this@respondEither.call.respond(it)
                else this@respondEither.call.respond<Any>(HttpStatusCode.OK, it)
            },
        )
}
