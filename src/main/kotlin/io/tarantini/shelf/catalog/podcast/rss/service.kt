@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast.rss

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.podcast.PodcastReadRepository
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.PodcastEpisodeId
import io.tarantini.shelf.catalog.podcast.domain.PodcastNotFound
import io.tarantini.shelf.catalog.podcast.domain.audioMimeType
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RSS_MIME = "application/rss+xml"
private const val RSS_SCHEMA_VERSION = 1

data class PodcastRssFeed(val xml: String, val etag: String)

data class PodcastRssAudio(val path: StoragePath, val size: Long, val mimeType: String)

interface PodcastRssService {
    context(_: RaiseContext)
    suspend fun generateFeed(token: FeedToken): PodcastRssFeed

    context(_: RaiseContext)
    suspend fun resolveAudio(token: FeedToken, episodeId: PodcastEpisodeId): PodcastRssAudio

    fun feedContentType(): String = RSS_MIME
}

fun podcastRssService(
    readRepository: PodcastReadRepository,
    podcastQueries: PodcastQueries,
    storageService: StorageService,
    publicRootUrl: String,
): PodcastRssService =
    DefaultPodcastRssService(
        readRepository = readRepository,
        podcastQueries = podcastQueries,
        storageService = storageService,
        publicRootUrl = publicRootUrl.trimEnd('/'),
    )

private class DefaultPodcastRssService(
    private val readRepository: PodcastReadRepository,
    private val podcastQueries: PodcastQueries,
    private val storageService: StorageService,
    private val publicRootUrl: String,
) : PodcastRssService {
    context(_: RaiseContext)
    override suspend fun generateFeed(token: FeedToken): PodcastRssFeed =
        withContext(Dispatchers.IO) {
            val podcast = ensureNotNull(readRepository.findByFeedToken(token)) { PodcastNotFound }
            ensure(podcast.isTokenValid(token, Clock.System.now())) { PodcastNotFound }
            val summary = podcastQueries.selectSummaryById(podcast.id.id).executeAsOne()
            val episodes =
                podcastQueries.selectRssEpisodesByPodcastId(podcast.id.id).executeAsList()

            val channelTitle = escapeXml(summary.series_title)
            val description = escapeXml("Private podcast feed for ${summary.series_title}")
            val channelLink = "$publicRootUrl/api/rss/podcasts/${token.value}"

            val items =
                episodes.joinToString("\n") { row ->
                    val enclosureUrl =
                        "$publicRootUrl/api/rss/podcasts/${token.value}/episodes/${row.episode_id.value}/audio"
                    val pubDate =
                        row.published_at
                            ?.toInstant()
                            ?.atZone(ZoneOffset.UTC)
                            ?.format(DateTimeFormatter.RFC_1123_DATE_TIME)
                    val guid = "${podcast.id.id.value}:${row.episode_id.value}"
                    buildString {
                        append("    <item>\n")
                        append("      <title>${escapeXml(row.title)}</title>\n")
                        append("      <guid isPermaLink=\"false\">${escapeXml(guid)}</guid>\n")
                        if (pubDate != null) {
                            append("      <pubDate>${escapeXml(pubDate)}</pubDate>\n")
                        }
                        append(
                            "      <enclosure url=\"${escapeXml(enclosureUrl)}\" length=\"${row.audio_size}\" type=\"${audioMimeType(row.audio_path.value)}\" />\n"
                        )
                        append("      <itunes:season>${row.season}</itunes:season>\n")
                        append("      <itunes:episode>${row.episode}</itunes:episode>\n")
                        append("    </item>")
                    }
                }

            val xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:podcast="https://podcastindex.org/namespace/1.0">
                  <channel>
                    <title>$channelTitle</title>
                    <description>$description</description>
                    <link>${escapeXml(channelLink)}</link>
                    <podcast:locked>yes</podcast:locked>
                $items
                  </channel>
                </rss>
                """
                    .trimIndent()

            PodcastRssFeed(
                xml = xml,
                etag = "${podcast.id.id.value}-${podcast.version}-v$RSS_SCHEMA_VERSION",
            )
        }

    context(_: RaiseContext)
    override suspend fun resolveAudio(
        token: FeedToken,
        episodeId: PodcastEpisodeId,
    ): PodcastRssAudio =
        withContext(Dispatchers.IO) {
            val podcast = ensureNotNull(readRepository.findByFeedToken(token)) { PodcastNotFound }
            ensure(podcast.isTokenValid(token, Clock.System.now())) { PodcastNotFound }
            val row =
                ensureNotNull(
                    podcastQueries
                        .selectPodcastEpisodeAudioByEpisodeId(podcast.id.id, episodeId)
                        .executeAsOneOrNull()
                ) {
                    PodcastNotFound
                }

            // Enforce storage-root confinement through StorageService.
            storageService.resolve(row.audio_path)
            PodcastRssAudio(
                path = row.audio_path,
                size = row.audio_size,
                mimeType = audioMimeType(row.audio_path.value),
            )
        }
}

private fun escapeXml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
