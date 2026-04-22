@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.Route
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.domain.PodcastRequest
import io.tarantini.shelf.catalog.podcast.domain.toCreateCommand
import io.tarantini.shelf.catalog.podcast.domain.toUpdateCommand
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.sharedCatalogMutation
import io.tarantini.shelf.user.auth.sharedCatalogRead
import kotlin.uuid.ExperimentalUuidApi

fun Route.podcastRoutes(podcastService: PodcastService, jwtService: JwtService) {
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
        sharedCatalogRead(jwtService) { respond({ podcastService.getPodcasts() }) }
    }

    get<PodcastsResource.Id> { resource ->
        sharedCatalogRead(jwtService) {
            respond({ podcastService.getPodcast(PodcastId(resource.id)) })
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

    delete<PodcastsResource.Id> { resource ->
        sharedCatalogMutation(jwtService) {
            respond(
                { podcastService.deletePodcast(PodcastId(resource.id)) },
                HttpStatusCode.NoContent,
            )
        }
    }
}
