package io.tarantini.shelf.integration.podcast.feed

import arrow.core.raise.catch
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.FeedParseFailed
import java.io.StringReader
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

data class ParsedFeed(
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String?,
    val audioUrl: String,
    val duration: Duration?,
    val publishedAt: Instant?,
    val season: Int?,
    val episode: Int?,
    val imageUrl: String?,
)

interface FeedParser {
    context(_: RaiseContext)
    suspend fun parse(xml: String): ParsedFeed
}

fun feedParser(): FeedParser = DomFeedParser()

private class DomFeedParser : FeedParser {
    context(_: RaiseContext)
    override suspend fun parse(xml: String): ParsedFeed =
        withContext(Dispatchers.IO) {
            val doc = catch({ parseXml(xml) }) { raise(FeedParseFailed) }
            val root = doc.documentElement ?: raise(FeedParseFailed)
            when (localName(root)) {
                "rss" -> parseRss(root)
                "feed" -> parseAtom(root)
                else -> raise(FeedParseFailed)
            }
        }

    context(_: RaiseContext)
    private fun parseRss(root: Element): ParsedFeed {
        val channel = firstChild(root, "channel") ?: raise(FeedParseFailed)
        val title = textOf(channel, "title") ?: raise(FeedParseFailed)
        val description = textOf(channel, "description")
        val imageUrl =
            itunesImage(channel)
                ?: firstChild(firstChild(channel, "image"), "url")?.textContent?.trim()
        val episodes = children(channel, "item").mapNotNull { parseRssItem(it) }
        return ParsedFeed(
            title = title,
            description = description,
            imageUrl = imageUrl,
            episodes = episodes,
        )
    }

    context(_: RaiseContext)
    private fun parseRssItem(item: Element): ParsedEpisode? {
        val guid = textOf(item, "guid") ?: textOf(item, "id") ?: textOf(item, "link") ?: return null
        val title = textOf(item, "title") ?: guid
        val audioUrl = enclosureUrl(item) ?: return null
        val description = textOf(item, "description")
        val duration = parseDuration(textOf(item, "duration", namespace = "itunes"))
        val publishedAt =
            parseTimestamp(
                textOf(item, "pubDate") ?: textOf(item, "published") ?: textOf(item, "updated")
            )
        val season = textOf(item, "season", namespace = "itunes")?.toIntOrNull()
        val episode = textOf(item, "episode", namespace = "itunes")?.toIntOrNull()
        val imageUrl =
            itunesImage(item) ?: firstChild(firstChild(item, "image"), "url")?.textContent?.trim()
        return ParsedEpisode(
            guid = guid,
            title = title,
            description = description,
            audioUrl = audioUrl,
            duration = duration,
            publishedAt = publishedAt,
            season = season,
            episode = episode,
            imageUrl = imageUrl,
        )
    }

    context(_: RaiseContext)
    private fun parseAtom(feed: Element): ParsedFeed {
        val title = textOf(feed, "title") ?: raise(FeedParseFailed)
        val description = textOf(feed, "subtitle")
        val imageUrl = textOf(feed, "logo") ?: textOf(feed, "icon")
        val episodes = children(feed, "entry").mapNotNull { parseAtomEntry(it) }
        return ParsedFeed(
            title = title,
            description = description,
            imageUrl = imageUrl,
            episodes = episodes,
        )
    }

    private fun parseAtomEntry(entry: Element): ParsedEpisode? {
        val guid = textOf(entry, "id") ?: textOf(entry, "link") ?: return null
        val title = textOf(entry, "title") ?: guid
        val description = textOf(entry, "summary") ?: textOf(entry, "content")
        val audioUrl = atomEnclosure(entry) ?: return null
        val publishedAt = parseTimestamp(textOf(entry, "published") ?: textOf(entry, "updated"))
        return ParsedEpisode(
            guid = guid,
            title = title,
            description = description,
            audioUrl = audioUrl,
            duration = null,
            publishedAt = publishedAt,
            season = null,
            episode = null,
            imageUrl = null,
        )
    }
}

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }

private fun parseXml(xml: String): Document {
    val builder = secureDocumentBuilderFactory().newDocumentBuilder()
    return builder.parse(InputSource(StringReader(xml)))
}

private fun localName(node: Node): String = node.localName ?: node.nodeName.substringAfterLast(':')

private fun firstChild(parent: Element?, name: String): Element? {
    if (parent == null) return null
    val nodes = parent.childNodes
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n.nodeType == Node.ELEMENT_NODE && localName(n) == name) return n as Element
    }
    return null
}

private fun children(parent: Element, name: String): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = parent.childNodes
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n.nodeType == Node.ELEMENT_NODE && localName(n) == name) result += n as Element
    }
    return result
}

private fun textOf(parent: Element, name: String, namespace: String? = null): String? {
    val nodes = parent.childNodes
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n.nodeType != Node.ELEMENT_NODE) continue
        val isNameMatch = localName(n) == name
        val nsPrefix = n.nodeName.substringBefore(':', missingDelimiterValue = "")
        val isNamespaceMatch = namespace == null || nsPrefix == namespace
        if (isNameMatch && isNamespaceMatch)
            return n.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }
    return null
}

private fun itunesImage(parent: Element): String? {
    val nodes = parent.childNodes
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n.nodeType != Node.ELEMENT_NODE) continue
        if (localName(n) == "image" && n.nodeName.startsWith("itunes:")) {
            val href = (n as Element).getAttribute("href").trim()
            if (href.isNotEmpty()) return href
        }
    }
    return null
}

private fun enclosureUrl(item: Element): String? {
    val nodes = item.childNodes
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n.nodeType == Node.ELEMENT_NODE && localName(n) == "enclosure") {
            val url = (n as Element).getAttribute("url").trim()
            if (url.isNotEmpty()) return url
        }
    }
    return null
}

private fun atomEnclosure(entry: Element): String? {
    val nodes = entry.childNodes
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n.nodeType != Node.ELEMENT_NODE || localName(n) != "link") continue
        val el = n as Element
        val rel = el.getAttribute("rel").trim()
        if (rel == "enclosure" || rel.isEmpty()) {
            val href = el.getAttribute("href").trim()
            if (href.isNotEmpty()) return href
        }
    }
    return null
}

private fun parseDuration(raw: String?): Duration? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    trimmed.toLongOrNull()?.let {
        return it.seconds
    }

    val parts = trimmed.split(":").map { it.trim() }
    if (parts.size !in 2..3) return null
    val numbers = parts.map { it.toLongOrNull() ?: return null }
    val seconds =
        when (numbers.size) {
            2 -> numbers[0] * 60 + numbers[1]
            3 -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
            else -> return null
        }
    return seconds.seconds
}

private fun parseTimestamp(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
            ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        }
        .recoverCatching { OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching { java.time.Instant.parse(raw) }
        .getOrNull()
        ?.let { Instant.fromEpochMilliseconds(it.toEpochMilli()) }
}
