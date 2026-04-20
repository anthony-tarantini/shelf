@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.opds

import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.series.domain.SeriesId
import kotlin.uuid.ExperimentalUuidApi

class OpdsLinkBuilder(private val baseUrl: String, private val rootUrl: String) {
    fun catalog() = "$baseUrl/catalog"

    fun books(page: Int? = null, size: Int? = null): String {
        return if (page != null && size != null) "$baseUrl/books?page=$page&size=$size"
        else "$baseUrl/books"
    }

    fun authors() = "$baseUrl/authors"

    fun authorBooks(authorId: AuthorId, page: Int? = null, size: Int? = null): String {
        return if (page != null && size != null)
            "$baseUrl/authors/${authorId.value}?page=$page&size=$size"
        else "$baseUrl/authors/${authorId.value}"
    }

    fun series() = "$baseUrl/series"

    fun seriesBooks(seriesId: SeriesId, page: Int? = null, size: Int? = null): String {
        return if (page != null && size != null)
            "$baseUrl/series/${seriesId.value}?page=$page&size=$size"
        else "$baseUrl/series/${seriesId.value}"
    }

    fun search() = "$baseUrl/search"

    fun searchDescription() = "$baseUrl/search/description"

    fun download(bookId: String) = "$rootUrl/api/books/$bookId/download"

    fun cover(bookId: String) = "$rootUrl/api/books/$bookId/cover"

    fun thumbnail(bookId: String) = "$rootUrl/api/books/$bookId/thumbnail"
}

class OpdsFeedBuilder(private val links: OpdsLinkBuilder, private val updated: String) {
    fun buildFeed(
        id: String,
        title: String,
        selfHref: String,
        upHref: String? = null,
        type: String = OpdsMimeType.ATOM,
        entries: List<OpdsEntry> = emptyList(),
        page: Int? = null,
        size: Int? = null,
        totalCount: Long? = null,
        includeSearch: Boolean = false,
    ): OpdsFeed {
        val feedLinks =
            mutableListOf(
                OpdsLink(href = selfHref, rel = OpdsRel.SELF, type = type),
                OpdsLink(href = links.catalog(), rel = OpdsRel.START, type = OpdsMimeType.ATOM),
            )
        upHref?.let {
            feedLinks.add(OpdsLink(href = it, rel = OpdsRel.UP, type = OpdsMimeType.ATOM))
        }

        if (includeSearch) {
            feedLinks.add(
                OpdsLink(
                    href = links.searchDescription(),
                    rel = OpdsRel.SEARCH,
                    type = OpdsMimeType.OPENSEARCH,
                )
            )
        }

        if (page != null && size != null && totalCount != null) {
            val basePaginationUrl = selfHref.substringBefore("?")
            if ((page + 1) * size < totalCount) {
                val nextHref = "$basePaginationUrl?page=${page + 1}&size=$size"
                feedLinks.add(OpdsLink(href = nextHref, rel = OpdsRel.NEXT, type = type))
            }
            if (page > 0) {
                val prevHref = "$basePaginationUrl?page=${page - 1}&size=$size"
                feedLinks.add(OpdsLink(href = prevHref, rel = OpdsRel.PREVIOUS, type = type))
            }
        }

        return OpdsFeed(
            id = id,
            title = title,
            updated = updated,
            link = feedLinks,
            entry = entries,
        )
    }
}

object OpdsMediaType {
    fun getLabel(mimeType: String): String =
        when (mimeType) {
            OpdsMimeType.PDF -> "PDF"
            OpdsMimeType.MOBI -> "MOBI"
            OpdsMimeType.AZW3 -> "AZW3"
            OpdsMimeType.CBZ -> "CBZ"
            else -> "EPUB"
        }
}
