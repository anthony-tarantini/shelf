@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast.rss

import arrow.core.raise.context.either
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.tarantini.shelf.app.AppError
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.processing.storage.StorageService
import kotlin.uuid.ExperimentalUuidApi

fun Route.podcastRssRoutes(rssService: PodcastRssService, storageService: StorageService) {
    get("/rss/podcasts/{token}") {
        either {
                val token = FeedToken(call.parameters["token"])
                rssService.generateFeed(token)
            }
            .fold(
                { err: AppError -> respond(err) },
                { feed ->
                    val quotedEtag = "\"${feed.etag}\""
                    val ifNoneMatch = call.request.headers[HttpHeaders.IfNoneMatch]?.trim()
                    if (ifNoneMatch == quotedEtag) {
                        call.respond(HttpStatusCode.NotModified)
                        return@get
                    }
                    call.response.header(HttpHeaders.ETag, quotedEtag)
                    call.respondText(feed.xml, ContentType.parse(rssService.feedContentType()))
                },
            )
    }

    get("/rss/podcasts/{token}/episodes/{bookId}/audio") {
        either {
                val token = FeedToken(call.parameters["token"])
                val bookId = BookId(call.parameters["bookId"])
                rssService.resolveAudio(token, bookId)
            }
            .fold(
                { err: AppError -> respond(err) },
                { audio ->
                    either { storageService.getReadChannel(audio.path) }
                        .fold(
                            { err: AppError -> respond(err) },
                            { (length, channel) ->
                                call.respond(
                                    object : OutgoingContent.ReadChannelContent() {
                                        override val contentLength = length
                                        override val contentType = ContentType.parse(audio.mimeType)

                                        override fun readFrom() = channel
                                    }
                                )
                            },
                        )
                },
            )
    }
}
