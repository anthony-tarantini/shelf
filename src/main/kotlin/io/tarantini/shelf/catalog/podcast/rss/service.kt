@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast.rss

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.podcast.PodcastReadRepository
import io.tarantini.shelf.catalog.podcast.domain.EpisodeMapping
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.PodcastEpisodeId
import io.tarantini.shelf.catalog.podcast.domain.PodcastNotFound
import io.tarantini.shelf.catalog.podcast.domain.audioMimeType
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.catalog.podcast.persistence.SelectRssEpisodesByPodcastId
import io.tarantini.shelf.integration.podcast.feed.parseFeedDocument
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import java.io.StringWriter
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val RSS_MIME = "application/rss+xml"
private const val RSS_SCHEMA_VERSION = 2
private const val RSS_REWRITE_VERSION = 3

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
            val root = (baseUrl ?: publicRootUrl).trimEnd('/')

            val cachedXml = readRepository.getCachedFeedXml(podcast.id.id)
            if (cachedXml != null) {
                val mappings =
                    readRepository.listMappings(podcast.id.id).associateBy { it.upstreamGuid.value }
                val hostedById =
                    podcastQueries
                        .selectRssEpisodesByPodcastId(podcast.id.id)
                        .executeAsList()
                        .associateBy { it.episode_id }
                val rewritten =
                    rewriteUpstreamFeed(
                        rawXml = cachedXml,
                        token = token,
                        baseUrl = root,
                        mappings = mappings,
                        hostedById = hostedById,
                    )
                val cached = readRepository.getCachedFeed(podcast.id.id)
                val fetchedAtEpoch = cached?.fetchedAt?.toEpochMilliseconds() ?: 0L
                return@withContext PodcastRssFeed(
                    xml = rewritten,
                    etag =
                        "${podcast.id.id.value}-${podcast.version}-$fetchedAtEpoch-v$RSS_REWRITE_VERSION",
                )
            }

            val summary = podcastQueries.selectSummaryById(podcast.id.id).executeAsOne()
            val episodes =
                podcastQueries.selectRssEpisodesByPodcastId(podcast.id.id).executeAsList()
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

context(_: RaiseContext)
private fun rewriteUpstreamFeed(
    rawXml: String,
    token: FeedToken,
    baseUrl: String,
    mappings: Map<String, EpisodeMapping>,
    hostedById: Map<PodcastEpisodeId, SelectRssEpisodesByPodcastId>,
): String {
    val doc = parseFeedDocument(rawXml)
    val items = doc.getElementsByTagName("item")
    for (i in 0 until items.length) {
        val item = items.item(i) as? Element
        if (item != null) {
            rewriteItemEnclosure(item, token, baseUrl, mappings, hostedById)
        }
    }
    return serializeDocument(doc)
}

private fun rewriteItemEnclosure(
    item: Element,
    token: FeedToken,
    baseUrl: String,
    mappings: Map<String, EpisodeMapping>,
    hostedById: Map<PodcastEpisodeId, SelectRssEpisodesByPodcastId>,
) {
    val guidText = firstChildText(item, "guid") ?: return
    val mapping = mappings[guidText] ?: return
    val hostedId = mapping.hostedEpisodeId ?: return
    val hosted = hostedById[hostedId] ?: return
    val enclosure = firstChildElement(item, "enclosure") ?: return
    val rewrittenUrl = "$baseUrl/api/rss/podcasts/${token.value}/episodes/${hostedId.value}/audio"
    enclosure.setAttribute("url", rewrittenUrl)
    enclosure.setAttribute("length", hosted.audio_size.toString())
    enclosure.setAttribute("type", audioMimeType(hosted.audio_path.value))
}

private fun firstChildElement(parent: Element, name: String): Element? {
    val nodes = parent.childNodes
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n.nodeType == Node.ELEMENT_NODE) {
            val ln = n.localName ?: n.nodeName.substringAfterLast(':')
            if (ln == name) return n as Element
        }
    }
    return null
}

private fun firstChildText(parent: Element, name: String): String? =
    firstChildElement(parent, name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

private fun serializeDocument(doc: org.w3c.dom.Document): String {
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    val writer = StringWriter()
    transformer.transform(DOMSource(doc), StreamResult(writer))
    return writer.toString()
}
