@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast.rss

import arrow.core.raise.context.either
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.tarantini.shelf.app.AppError
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.processing.storage.StorageService
import java.io.RandomAccessFile
import java.nio.file.Files
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
                    either {
                            val resolvedPath = storageService.resolve(audio.path)
                            val totalSize = Files.size(resolvedPath)
                            val range =
                                parseByteRange(call.request.headers[HttpHeaders.Range], totalSize)

                            if (range is ByteRangeParse.Invalid) {
                                call.response.header(HttpHeaders.ContentRange, "bytes */$totalSize")
                                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                                return@either null
                            }

                            if (range is ByteRangeParse.Valid) {
                                val contentLength = range.endInclusive - range.start + 1
                                val contentBytes =
                                    readByteRange(
                                        resolvedPath,
                                        start = range.start,
                                        byteCount = contentLength,
                                    )
                                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                call.response.header(
                                    HttpHeaders.ContentRange,
                                    "bytes ${range.start}-${range.endInclusive}/$totalSize",
                                )
                                call.respondBytes(
                                    bytes = contentBytes,
                                    contentType = ContentType.parse(audio.mimeType),
                                    status = HttpStatusCode.PartialContent,
                                )
                                return@either null
                            }

                            val (length, channel) = storageService.getReadChannel(audio.path)
                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                            call.respond(
                                object : OutgoingContent.ReadChannelContent() {
                                    override val contentLength = length
                                    override val contentType = ContentType.parse(audio.mimeType)

                                    override fun readFrom() = channel
                                }
                            )
                            null
                        }
                        .fold({ err: AppError -> respond(err) }, { _ -> })
                },
            )
    }
}

private sealed interface ByteRangeParse {
    data object None : ByteRangeParse

    data class Valid(val start: Long, val endInclusive: Long) : ByteRangeParse

    data object Invalid : ByteRangeParse
}

private fun parseByteRange(header: String?, totalSize: Long): ByteRangeParse {
    if (header.isNullOrBlank()) return ByteRangeParse.None
    val raw = header.trim()
    if (!raw.startsWith("bytes=")) return ByteRangeParse.Invalid
    val spec = raw.removePrefix("bytes=").trim()
    if (spec.contains(",")) return ByteRangeParse.Invalid
    if (spec.isBlank()) return ByteRangeParse.Invalid

    val (startPart, endPart) =
        spec.split('-', limit = 2).let {
            if (it.size != 2) return ByteRangeParse.Invalid
            it[0].trim() to it[1].trim()
        }

    if (totalSize <= 0L) return ByteRangeParse.Invalid

    return when {
        startPart.isNotEmpty() -> {
            val start = startPart.toLongOrNull() ?: return ByteRangeParse.Invalid
            if (start < 0L || start >= totalSize) return ByteRangeParse.Invalid
            val end =
                if (endPart.isEmpty()) totalSize - 1L
                else endPart.toLongOrNull() ?: return ByteRangeParse.Invalid
            if (end < start) return ByteRangeParse.Invalid
            ByteRangeParse.Valid(start = start, endInclusive = minOf(end, totalSize - 1L))
        }
        else -> {
            val suffixLength = endPart.toLongOrNull() ?: return ByteRangeParse.Invalid
            if (suffixLength <= 0L) return ByteRangeParse.Invalid
            val clamped = minOf(suffixLength, totalSize)
            ByteRangeParse.Valid(start = totalSize - clamped, endInclusive = totalSize - 1L)
        }
    }
}

private fun readByteRange(path: java.nio.file.Path, start: Long, byteCount: Long): ByteArray {
    require(byteCount >= 0L) { "byteCount must be non-negative." }
    require(byteCount <= Int.MAX_VALUE.toLong()) { "byteCount too large to materialize." }
    val bytes = ByteArray(byteCount.toInt())
    RandomAccessFile(path.toFile(), "r").use { file ->
        file.seek(start)
        file.readFully(bytes)
    }
    return bytes
}
