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
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RSS_MIME = "application/rss+xml"
private const val RSS_SCHEMA_VERSION = 2

data class PodcastRssFeed(val xml: String, val etag: String)

data class PodcastRssAudio(val path: StoragePath, val size: Long, val mimeType: String)

data class PodcastRssCover(val path: StoragePath)

interface PodcastRssService {
    context(_: RaiseContext)
    suspend fun generateFeed(token: FeedToken, baseUrl: String? = null): PodcastRssFeed

    context(_: RaiseContext)
    suspend fun resolveAudio(token: FeedToken, episodeId: PodcastEpisodeId): PodcastRssAudio

    context(_: RaiseContext)
    suspend fun resolveCover(token: FeedToken): PodcastRssCover

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
    override suspend fun generateFeed(token: FeedToken, baseUrl: String?): PodcastRssFeed =
        withContext(Dispatchers.IO) {
            val podcast = ensureNotNull(readRepository.findByFeedToken(token)) { PodcastNotFound }
            ensure(podcast.isTokenValid(token, Clock.System.now())) { PodcastNotFound }
            val summary = podcastQueries.selectSummaryById(podcast.id.id).executeAsOne()
            val episodes =
                podcastQueries.selectRssEpisodesByPodcastId(podcast.id.id).executeAsList()

            val root = (baseUrl ?: publicRootUrl).trimEnd('/')
            val channelTitle = escapeXml(summary.series_title)
            val description = escapeXml("Private podcast feed for ${summary.series_title}")
            val channelLink = "$root/api/rss/podcasts/${token.value}"
            val coverUrl = summary.cover_path?.let { "$root/api/rss/podcasts/${token.value}/cover" }
            val channelAuthor =
                episodes.firstNotNullOfOrNull { it.author?.takeIf(String::isNotBlank) }
                    ?: CHANNEL_AUTHOR
            val channelSummary =
                episodes.firstNotNullOfOrNull { it.description?.takeIf(String::isNotBlank) }
                    ?: summary.series_title
            val nowRfc1123 =
                DateTimeFormatter.RFC_1123_DATE_TIME.format(
                    Clock.System.now().toJavaInstant().atZone(ZoneOffset.UTC)
                )
            val channelPubDate =
                episodes
                    .mapNotNull { it.published_at }
                    .maxOrNull()
                    ?.toInstant()
                    ?.atZone(ZoneOffset.UTC)
                    ?.format(DateTimeFormatter.RFC_1123_DATE_TIME) ?: nowRfc1123

            val xml = buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                append(
                    "<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\" xmlns:podcast=\"https://podcastindex.org/namespace/1.0\">\n"
                )
                append("  <channel>\n")
                append("    <title>$channelTitle</title>\n")
                append("    <description>$description</description>\n")
                append("    <link>${escapeXml(channelLink)}</link>\n")
                append("    <pubDate>${escapeXml(channelPubDate)}</pubDate>\n")
                append("    <lastBuildDate>${escapeXml(nowRfc1123)}</lastBuildDate>\n")
                append("    <language>en-us</language>\n")
                append("    <itunes:author>${escapeXml(channelAuthor)}</itunes:author>\n")
                append("    <itunes:summary>${escapeXml(channelSummary)}</itunes:summary>\n")
                append("    <itunes:explicit>no</itunes:explicit>\n")
                append("    <itunes:category text=\"Fiction\" />\n")
                if (coverUrl != null) {
                    append("    <itunes:image href=\"${escapeXml(coverUrl)}\" />\n")
                }
                append("    <podcast:locked>yes</podcast:locked>\n")
                episodes.forEach { row ->
                    val enclosureUrl =
                        "$root/api/rss/podcasts/${token.value}/episodes/${row.episode_id.value}/audio"
                    val pubDate =
                        row.published_at
                            ?.toInstant()
                            ?.atZone(ZoneOffset.UTC)
                            ?.format(DateTimeFormatter.RFC_1123_DATE_TIME) ?: nowRfc1123
                    val guid = "${podcast.id.id.value}:${row.episode_id.value}"
                    val duration =
                        row.total_time?.takeIf { it > 0.0 }?.let(::formatDuration)
                            ?: estimateDurationFromSize(row.audio_size)
                    append("    <item>\n")
                    append("      <title>${escapeXml(row.title)}</title>\n")
                    append("      <guid isPermaLink=\"false\">${escapeXml(guid)}</guid>\n")
                    append("      <pubDate>${escapeXml(pubDate)}</pubDate>\n")
                    append(
                        "      <enclosure url=\"${escapeXml(enclosureUrl)}\" length=\"${row.audio_size}\" type=\"${audioMimeType(row.audio_path.value)}\" />\n"
                    )
                    append("      <itunes:season>${row.season}</itunes:season>\n")
                    append("      <itunes:episode>${row.episode}</itunes:episode>\n")
                    append("      <itunes:duration>$duration</itunes:duration>\n")
                    append("    </item>\n")
                }
                append("  </channel>\n")
                append("</rss>\n")
            }

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

    context(_: RaiseContext)
    override suspend fun resolveCover(token: FeedToken): PodcastRssCover =
        withContext(Dispatchers.IO) {
            val podcast = ensureNotNull(readRepository.findByFeedToken(token)) { PodcastNotFound }
            ensure(podcast.isTokenValid(token, Clock.System.now())) { PodcastNotFound }
            val summary = podcastQueries.selectSummaryById(podcast.id.id).executeAsOne()
            val coverPath = ensureNotNull(summary.cover_path) { PodcastNotFound }
            storageService.resolve(coverPath)
            PodcastRssCover(path = coverPath)
        }
}

private const val CHANNEL_AUTHOR = "Shelf Podcasts"

private const val ESTIMATED_BYTES_PER_SECOND = 8_000L // ~64 kbps spoken-word audio

private fun estimateDurationFromSize(audioSize: Long): String =
    formatDuration((audioSize.coerceAtLeast(0L) / ESTIMATED_BYTES_PER_SECOND).toDouble())

private fun formatDuration(totalSeconds: Double): String {
    val seconds = totalSeconds.toLong().coerceAtLeast(0L)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun escapeXml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
