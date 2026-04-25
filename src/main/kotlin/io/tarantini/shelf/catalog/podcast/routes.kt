@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.context.ensureNotNull
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
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.tarantini.shelf.app.AppError
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.podcast.domain.*
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.sharedCatalogMutation
import io.tarantini.shelf.user.auth.sharedCatalogRead
import kotlin.uuid.ExperimentalUuidApi

fun Route.podcastRoutes(
    podcastService: PodcastService,
    jwtService: JwtService,
    storageService: StorageService,
) {
    post<PodcastsResource> {
        sharedCatalogMutation(jwtService) {
            respond(
                {
                    val request = call.receive<Request<PodcastRequest>>().data
                    podcastService.createPodcast(request.toCreateCommand())
                },
                HttpStatusCode.Created,
            )
        }
    }

    get<PodcastsResource> {
        sharedCatalogRead(jwtService) { respond({ podcastService.getDashboard() }) }
    }

    get<PodcastsResource.Id> { resource ->
        sharedCatalogRead(jwtService) {
            respond({ podcastService.getPodcastAggregate(PodcastId(resource.id)) })
        }
    }

    get<PodcastsResource.Id.Cover> { resource ->
        sharedCatalogRead(jwtService) {
            either {
                    val aggregate =
                        podcastService.getPodcastAggregate(PodcastId(resource.parent.id))
                    val coverPath =
                        ensureNotNull(aggregate.episodes.firstNotNullOfOrNull { it.coverPath }) {
                            PodcastNotFound
                        }
                    val (length, channel) = storageService.getReadChannel(coverPath)
                    call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
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
                .fold({ err: AppError -> respond(err) }, { content -> call.respond(content) })
        }
    }

    get<PodcastsResource.Id.EpisodeCover> { resource ->
        sharedCatalogRead(jwtService) {
            either {
                    val aggregate =
                        podcastService.getPodcastAggregate(PodcastId(resource.parent.id))
                    val episodeId = PodcastEpisodeId(resource.episodeId)
                    val coverPath =
                        ensureNotNull(
                            aggregate.episodes.firstOrNull { it.id == episodeId }?.coverPath
                        ) {
                            PodcastNotFound
                        }
                    val (length, channel) = storageService.getReadChannel(coverPath)
                    call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
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
                .fold({ err: AppError -> respond(err) }, { content -> call.respond(content) })
        }
    }

    put<PodcastsResource.Id> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({
                val request = call.receive<Request<PodcastRequest>>().data
                podcastService.updatePodcast(request.toUpdateCommand(resource.id))
            })
        }
    }

    post<PodcastsResource.Id.RotateToken> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({ podcastService.rotateToken(PodcastId(resource.parent.id)) })
        }
    }

    post<PodcastsResource.Id.RevokeToken> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({ podcastService.revokeToken(PodcastId(resource.parent.id)) })
        }
    }

    delete<PodcastsResource.Id.Credentials> { resource ->
        sharedCatalogMutation(jwtService) {
            respond(
                { podcastService.clearFeedCredentials(PodcastId(resource.parent.id)) },
                HttpStatusCode.NoContent,
            )
        }
    }

    delete<PodcastsResource.Id> { resource ->
        sharedCatalogMutation(jwtService) {
            respond(
                { podcastService.deletePodcast(PodcastId(resource.id)) },
                HttpStatusCode.NoContent,
            )
        }
    }
}
