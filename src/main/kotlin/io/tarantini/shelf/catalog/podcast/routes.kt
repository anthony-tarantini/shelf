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
import io.tarantini.shelf.catalog.podcast.domain.*
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.sharedCatalogMutation
import io.tarantini.shelf.user.auth.sharedCatalogRead
import kotlin.uuid.ExperimentalUuidApi

fun Route.podcastRoutes(
    podcastService: PodcastService,
    jwtService: JwtService,
    libationService: PodcastLibationService,
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

    delete<PodcastsResource.Id> { resource ->
        sharedCatalogMutation(jwtService) {
            respond(
                { podcastService.deletePodcast(PodcastId(resource.id)) },
                HttpStatusCode.NoContent,
            )
        }
    }

    post<PodcastsResource.Libation.Scan> {
        sharedCatalogMutation(jwtService) { respond({ libationService.scanNow() }) }
    }

    get<PodcastsResource.Libation.Status> {
        sharedCatalogRead(jwtService) { respond({ libationService.getStatus() }) }
    }
}
