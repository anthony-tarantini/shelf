@file:OptIn(ExperimentalSerializationApi::class)

package io.tarantini.shelf.catalog.series

import arrow.core.raise.either
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.header
import io.ktor.server.routing.Route
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.id
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.author.SeriesAuthorProvider
import io.tarantini.shelf.catalog.book.SeriesBookSummaryProvider
import io.tarantini.shelf.catalog.book.respondEither
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.domain.SeriesRequest
import io.tarantini.shelf.catalog.series.domain.toCreateCommand
import io.tarantini.shelf.catalog.series.domain.toUpdateCommand
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.user.activity.ActivityService
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.sharedCatalogMutation
import io.tarantini.shelf.user.auth.sharedCatalogRead
import kotlinx.serialization.ExperimentalSerializationApi

fun Route.seriesRoutes(
    seriesService: SeriesService,
    bookSummaryProvider: SeriesBookSummaryProvider,
    authorProvider: SeriesAuthorProvider,
    activityService: ActivityService,
    storageService: StorageService,
    jwtService: JwtService,
) {
    post<SeriesResource> {
        sharedCatalogMutation(jwtService) {
            respond(
                {
                    val request = call.receive<Request<SeriesRequest>>().data
                    seriesService.createSeries(request.toCreateCommand())
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
            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
            respondEither {
                val coverPath =
                    either { seriesService.getPreferredCoverPath(SeriesId(resource.id)) }.bind()
                val (length, channel) = either { storageService.getReadChannel(coverPath) }.bind()
                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength = length
                    override val contentType =
                        when (coverPath.extension().lowercase()) {
                            "png" -> ContentType.Image.PNG
                            "webp" -> ContentType.parse("image/webp")
                            else -> ContentType.Image.JPEG
                        }

                    override fun readFrom() = channel
                }
            }
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
                    activityService.enrichBookSummaries(
                        bookSummaryProvider.getBookSummariesForSeries(id)
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
                seriesService.updateSeries(request.toUpdateCommand(resource.id))
            })
        }
    }

    delete<SeriesResource.Id> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({ seriesService.deleteSeries(SeriesId(resource.id)) }, HttpStatusCode.NoContent)
        }
    }
}
