@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.author

import arrow.core.raise.context.either
import arrow.core.raise.context.ensureNotNull
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.header
import io.ktor.server.routing.Route
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.id
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.domain.AuthorRequest
import io.tarantini.shelf.catalog.author.domain.AuthorRoot
import io.tarantini.shelf.catalog.book.BookService
import io.tarantini.shelf.catalog.book.domain.BookSummary
import io.tarantini.shelf.catalog.book.respondEither
import io.tarantini.shelf.catalog.metadata.domain.EmptySearchQuery
import io.tarantini.shelf.integration.core.ExternalMetadataProvider
import io.tarantini.shelf.processing.import.domain.MissingFile
import io.tarantini.shelf.processing.storage.FileBytes
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.processing.storage.fetchRemoteImage
import io.tarantini.shelf.processing.storage.inferImageExtension
import io.tarantini.shelf.processing.storage.readBoundedImageBytes
import io.tarantini.shelf.processing.storage.validateImage
import io.tarantini.shelf.user.activity.ActivityService
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.sharedCatalogMutation
import io.tarantini.shelf.user.auth.sharedCatalogRead
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable private data class ImageUrlRequest(val url: String)

private const val HARDCOVER_HOST = "hardcover.app"

context(_: io.tarantini.shelf.RaiseContext)
private fun fetchRemoteAuthorImage(url: String): Pair<ByteArray, String> =
    fetchRemoteImage(url, listOf(HARDCOVER_HOST))

fun Route.authorRoutes(
    authorService: AuthorService,
    bookService: BookService,
    activityService: ActivityService,
    jwtService: JwtService,
    storageService: StorageService,
    externalMetadataProvider: ExternalMetadataProvider,
) {
    get<AuthorsResource> {
        sharedCatalogRead(jwtService) { respond({ authorService.getAuthors() }) }
    }

    get<AuthorsResource.Page> { resource ->
        sharedCatalogRead(jwtService) {
            respond({
                authorService.getAuthorsPage(
                    resource.page,
                    resource.size,
                    resource.sortBy,
                    resource.sortDir,
                )
            })
        }
    }

    get<AuthorsResource.Id> { resource ->
        sharedCatalogRead(jwtService) {
            respond({ authorService.getAuthor(AuthorId(resource.id)) })
        }
    }

    get<AuthorsResource.Id.Details> { resource ->
        sharedCatalogRead(jwtService) {
            respond({ authorService.getAuthorAggregate(AuthorId(resource.id)) })
        }
    }

    put<AuthorsResource> {
        sharedCatalogMutation(jwtService) {
            respond({
                call.receive<Request<AuthorRequest>>().data.let {
                    authorService.updateAuthor(AuthorRoot.update(it.id, it.name))
                }
            })
        }
    }

    delete<AuthorsResource.Id> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({ authorService.deleteAuthor(AuthorId(resource.id)) }, HttpStatusCode.NoContent)
        }
    }

    get<AuthorsResource.Id.Books> { resource ->
        sharedCatalogRead(jwtService) { auth ->
            with(auth) {
                respond({
                    val authorId = AuthorId(resource.id)
                    val paged =
                        bookService.getBooksByAuthorPage(
                            authorId = authorId,
                            page = 0,
                            size = Int.MAX_VALUE,
                        )

                    activityService.enrichBookSummaries(
                        paged.items.map { agg ->
                            val series = agg.series.firstOrNull()
                            BookSummary(
                                id = agg.book.id.id,
                                title = agg.book.title,
                                coverPath = agg.book.coverPath,
                                authorNames = agg.authors.map { it.name },
                                seriesName = series?.name,
                                seriesIndex = series?.index,
                            )
                        }
                    )
                })
            }
        }
    }

    // Serve author image
    get<AuthorsResource.Id.Image> { resource ->
        sharedCatalogRead(jwtService) {
            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
            respondEither {
                val author = either { authorService.getAuthor(AuthorId(resource.id)) }.bind()
                val path = author.imagePath ?: raise(HttpStatusCode.NotFound)
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
    }

    // Upload author image (multipart file)
    post<AuthorsResource.Id.Image> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({
                val authorId = AuthorId(resource.id)
                val existingAuthor = authorService.getAuthor(authorId)
                val multipart = call.receiveMultipart()
                var imageBytes: ByteArray? = null
                var extension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val fileName = part.originalFileName ?: "image.jpg"
                            extension =
                                inferImageExtension(
                                    part.contentType?.toString()?.substringBefore(';'),
                                    fileName,
                                ) ?: fileName.substringAfterLast('.', "jpg").lowercase()
                            imageBytes = readBoundedImageBytes(part.provider().toInputStream())
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                val bytes = validateImage(ensureNotNull(imageBytes) { MissingFile }, extension)
                val storagePath = StoragePath.fromRaw("authors/${resource.id}/image.$extension")
                storageService.save(storagePath, FileBytes(bytes))
                authorService.updateAuthorImage(authorId, storagePath)
                existingAuthor.imagePath
                    ?.takeIf { it.value != storagePath.value }
                    ?.let {
                        storageService.delete(it)
                        storageService.delete(it.thumbnail())
                    }
                mapOf("imagePath" to storagePath.value)
            })
        }
    }

    // Apply external image by URL (downloads and stores locally)
    post<AuthorsResource.Id.Image.Url> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({
                val authorId = AuthorId(resource.id)
                val existingAuthor = authorService.getAuthor(authorId)
                val req = call.receive<Request<ImageUrlRequest>>().data
                val (bytes, extension) = fetchRemoteAuthorImage(req.url)
                val storagePath = StoragePath.fromRaw("authors/${resource.id}/image.$extension")
                storageService.save(storagePath, FileBytes(bytes))
                authorService.updateAuthorImage(authorId, storagePath)
                existingAuthor.imagePath
                    ?.takeIf { it.value != storagePath.value }
                    ?.let {
                        storageService.delete(it)
                        storageService.delete(it.thumbnail())
                    }
                mapOf("imagePath" to storagePath.value)
            })
        }
    }

    // Search Hardcover for author images
    get<AuthorsResource.Hardcover.Search> { resource ->
        sharedCatalogRead(jwtService) {
            respond({
                ensureNotNull(resource.query.takeIf { it.isNotBlank() }) { EmptySearchQuery }
                externalMetadataProvider.searchAuthorsByName(resource.query)
            })
        }
    }
}
