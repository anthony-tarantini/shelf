@file:OptIn(ExperimentalSerializationApi::class)

package io.tarantini.shelf.catalog.series

import arrow.core.raise.context.ensure
import arrow.core.raise.either
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.tarantini.shelf.app.AppError
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.id
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.author.SeriesAuthorProvider
import io.tarantini.shelf.catalog.book.SeriesBookProvider
import io.tarantini.shelf.catalog.book.domain.BookSummary
import io.tarantini.shelf.catalog.book.respondEither
import io.tarantini.shelf.catalog.series.domain.EmptySeriesTitle
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.domain.SeriesRequest
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.user.activity.ActivityService
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.sharedCatalogMutation
import io.tarantini.shelf.user.auth.sharedCatalogRead
import kotlinx.serialization.ExperimentalSerializationApi

fun Route.seriesRoutes(
    seriesService: SeriesService,
    bookProvider: SeriesBookProvider,
    authorProvider: SeriesAuthorProvider,
    activityService: ActivityService,
    storageService: StorageService,
    jwtService: JwtService,
) {
    suspend fun RoutingContext.respondCover(path: StoragePath) {
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

    post<SeriesResource> {
        sharedCatalogMutation(jwtService) {
            respond(
                {
                    val request = call.receive<Request<SeriesRequest>>().data
                    ensure(!request.title.isNullOrEmpty()) { EmptySeriesTitle }
                    seriesService.createSeries(request.title)
                },
                HttpStatusCode.Created,
            )
        }
    }

    get<SeriesResource> { sharedCatalogRead(jwtService) { respond({ seriesService.getSeries() }) } }

    get<SeriesResource.Page> { resource ->
        sharedCatalogRead(jwtService) {
            respond({
                seriesService.getSeriesPage(
                    resource.page,
                    resource.size,
                    resource.sortBy,
                    resource.sortDir,
                )
            })
        }
    }

    get<SeriesResource.Id> { resource ->
        sharedCatalogRead(jwtService) {
            respond({ seriesService.getSeries(SeriesId(resource.id)) })
        }
    }

    get<SeriesResource.Id.Cover> { resource ->
        sharedCatalogRead(jwtService) {
            either { seriesService.getPreferredCoverPath(SeriesId(resource.id)) }
                .fold(ifLeft = { err: AppError -> respond(err) }, ifRight = { respondCover(it) })
        }
    }

    get<SeriesResource.Id.Details> { resource ->
        sharedCatalogRead(jwtService) {
            respond({ seriesService.getSeriesAggregate(SeriesId(resource.id)) })
        }
    }

    get<SeriesResource.Id.Books> { resource ->
        sharedCatalogRead(jwtService) { auth ->
            with(auth) {
                respond({
                    val id = SeriesId(resource.id)
                    val books =
                        bookProvider.getBooksForSeries(listOf(id)).getOrDefault(id, emptyList())
                    activityService.enrichBookSummaries(
                        books.map {
                            BookSummary(id = it.id.id, title = it.title, coverPath = it.coverPath)
                        }
                    )
                })
            }
        }
    }

    get<SeriesResource.Id.Authors> { resource ->
        sharedCatalogRead(jwtService) {
            respond({
                val id = SeriesId(resource.id)
                authorProvider.getAuthorsForSeries(listOf(id)).getOrDefault(id, emptyList())
            })
        }
    }

    put<SeriesResource.Id> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({
                val request = call.receive<Request<SeriesRequest>>().data
                ensure(!request.title.isNullOrEmpty()) { EmptySeriesTitle }
                seriesService.updateSeries(SeriesId(resource.id), request.title)
            })
        }
    }

    delete<SeriesResource.Id> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({ seriesService.deleteSeries(SeriesId(resource.id)) }, HttpStatusCode.NoContent)
        }
    }
}
