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
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.integration.podcast.audible.AudibleAdapter
import io.tarantini.shelf.integration.podcast.audible.AudibleAuthService
import io.tarantini.shelf.integration.podcast.audible.AudibleTitle
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.sharedCatalogMutation
import io.tarantini.shelf.user.auth.sharedCatalogRead
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable

fun Route.podcastRoutes(
    podcastService: PodcastService,
    jwtService: JwtService,
    audibleAuthService: AudibleAuthService,
    audibleAdapter: AudibleAdapter,
    audibleContentFetchService: AudibleContentFetchService,
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
            respond({
                podcastService.rotateToken(PodcastId(resource.parent.id))
            })
        }
    }

    post<PodcastsResource.Id.RevokeToken> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({
                podcastService.revokeToken(PodcastId(resource.parent.id))
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

    delete<PodcastsResource.Id.Audible> { resource ->
        sharedCatalogMutation(jwtService) {
            respond({
                val podcastId = PodcastId(resource.parent.id)
                podcastService.clearFeedCredentials(podcastId)
            }, HttpStatusCode.NoContent)
        }
    }

    post<PodcastsResource.Audible.Connect> {
        sharedCatalogMutation(jwtService) { respond({ audibleAuthService.generateLoginUrl() }) }
    }

    post<PodcastsResource.Audible.Finalize> {
        sharedCatalogMutation(jwtService) {
            respond({
                val request = call.receive<Request<FinalizeAudibleAuthRequest>>().data
                audibleAuthService.finalizeAuth(request.sessionId, request.callbackUrl)
            })
        }
    }

    get<PodcastsResource.Audible.Library> {
        sharedCatalogRead(jwtService) {
            respond({
                // We'd usually get these from the session/user context
                // For this milestone, we'll try to find any existing Audible credentials
                // Or we can pass them in the header if the UI sends them.
                // For now, return empty if no credentials found.
                emptyList<AudibleTitle>()
            })
        }
    }

    post<PodcastsResource.Audible.Import> {
        sharedCatalogMutation(jwtService) {
            respond({
                val request = call.receive<Request<AudibleImportRequest>>().data
                audibleContentFetchService.importAudibleTitle(
                    request.asin,
                    SeriesId(request.seriesId),
                    request.autoFetch,
                    request.autoSanitize
                )
            }, HttpStatusCode.Created)
        }
    }
}

@Serializable data class FinalizeAudibleAuthRequest(val sessionId: String, val callbackUrl: String)

@Serializable
data class AudibleImportRequest(
    val asin: String,
    val seriesId: String,
    val autoFetch: Boolean,
    val autoSanitize: Boolean,
)
