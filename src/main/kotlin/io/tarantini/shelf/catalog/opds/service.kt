@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.opds

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.AuthorService
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.book.BookService
import io.tarantini.shelf.catalog.book.domain.SavedBookAggregate
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.coverMimeType
import io.tarantini.shelf.catalog.metadata.domain.ebookMimeType
import io.tarantini.shelf.catalog.search.SearchService
import io.tarantini.shelf.catalog.series.SeriesService
import io.tarantini.shelf.catalog.series.domain.SeriesId
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi

interface OpdsService {
    context(_: RaiseContext)
    suspend fun getRootCatalog(): OpdsFeed

    context(_: RaiseContext)
    suspend fun getAllBooksFeed(page: Int, size: Int): OpdsFeed

    context(_: RaiseContext)
    suspend fun getAuthorsFeed(): OpdsFeed

    context(_: RaiseContext)
    suspend fun getAuthorBooksFeed(authorId: AuthorId, page: Int, size: Int): OpdsFeed

    context(_: RaiseContext)
    suspend fun getSeriesFeed(): OpdsFeed

    context(_: RaiseContext)
    suspend fun getSeriesBooksFeed(seriesId: SeriesId, page: Int, size: Int): OpdsFeed

    context(_: RaiseContext)
    suspend fun getOpenSearchDescription(): OpenSearchDescription

    context(_: RaiseContext)
    suspend fun searchBooksFeed(query: String, page: Int, size: Int): OpdsFeed
}

fun opdsService(
    bookService: BookService,
    authorService: AuthorService,
    seriesService: SeriesService,
    searchService: SearchService,
    baseUrl: String,
    rootUrl: String,
): OpdsService =
    object : OpdsService {

        private val links = OpdsLinkBuilder(baseUrl, rootUrl)

        private fun now() = Instant.now().toString()

        private fun feedBuilder(updated: String) = OpdsFeedBuilder(links, updated)

        private fun mapBookToEntry(bookAggregate: SavedBookAggregate, updated: String): OpdsEntry {
            val book = bookAggregate.book
            val bookId = book.id.id.value.toString()
            val ebookEdition =
                bookAggregate.metadata
                    ?.editions
                    ?.find { it.edition.format == BookFormat.EBOOK }
                    ?.edition

            val entryLinks = mutableListOf<OpdsLink>()
            if (ebookEdition != null) {
                val mimeType = ebookMimeType(ebookEdition.path.value)
                val formatLabel = OpdsMediaType.getLabel(mimeType)
                entryLinks.add(
                    OpdsLink(
                        href = links.download(bookId),
                        rel = OpdsRel.ACQUISITION,
                        type = mimeType,
                        title = "Download $formatLabel",
                    )
                )
            }
            book.coverPath?.let {
                val coverMimeType = coverMimeType(it.value)
                entryLinks.add(
                    OpdsLink(href = links.cover(bookId), rel = OpdsRel.IMAGE, type = coverMimeType)
                )

                entryLinks.add(
                    OpdsLink(
                        href = links.thumbnail(bookId),
                        rel = OpdsRel.THUMBNAIL,
                        type = "image/jpeg",
                    )
                )
            }

            return OpdsEntry(
                id = "shelf:book:$bookId",
                title = book.title,
                updated = updated,
                author = bookAggregate.authors.map { OpdsAuthor(name = it.name) },
                summary = bookAggregate.metadata?.metadata?.description,
                link = entryLinks,
                issued = bookAggregate.metadata?.metadata?.published?.toString(),
                language = bookAggregate.metadata?.metadata?.language,
                publisher = bookAggregate.metadata?.metadata?.publisher,
            )
        }

        context(_: RaiseContext)
        override suspend fun getRootCatalog(): OpdsFeed {
            val updated = now()
            return feedBuilder(updated)
                .buildFeed(
                    id = "shelf:root",
                    title = "Shelf OPDS Catalog",
                    selfHref = links.catalog(),
                    includeSearch = true,
                    entries =
                        listOf(
                            OpdsEntry(
                                id = "shelf:allbooks",
                                title = "All Books",
                                updated = updated,
                                content = OpdsContent(type = "text", value = "Browse all e-books"),
                                link =
                                    listOf(
                                        OpdsLink(
                                            href = links.books(),
                                            rel = OpdsRel.SUBSECTION,
                                            type = OpdsMimeType.ATOM_ACQUISITION,
                                        )
                                    ),
                            ),
                            OpdsEntry(
                                id = "shelf:authors",
                                title = "Authors",
                                updated = updated,
                                content = OpdsContent(type = "text", value = "Browse by author"),
                                link =
                                    listOf(
                                        OpdsLink(
                                            href = links.authors(),
                                            rel = OpdsRel.SUBSECTION,
                                            type = OpdsMimeType.ATOM,
                                        )
                                    ),
                            ),
                            OpdsEntry(
                                id = "shelf:series",
                                title = "Series",
                                updated = updated,
                                content = OpdsContent(type = "text", value = "Browse by series"),
                                link =
                                    listOf(
                                        OpdsLink(
                                            href = links.series(),
                                            rel = OpdsRel.SUBSECTION,
                                            type = OpdsMimeType.ATOM,
                                        )
                                    ),
                            ),
                        ),
                )
        }

        context(_: RaiseContext)
        override suspend fun getAllBooksFeed(page: Int, size: Int): OpdsFeed {
            val updated = now()
            val bookPage = bookService.getBooksPage(page, size, format = BookFormat.EBOOK)
            val entries = bookPage.items.map { mapBookToEntry(it, updated) }

            return feedBuilder(updated)
                .buildFeed(
                    id = "shelf:allbooks",
                    title = "All Books",
                    selfHref = links.books(page, size),
                    upHref = links.catalog(),
                    type = OpdsMimeType.ATOM_ACQUISITION,
                    entries = entries,
                    page = page,
                    size = size,
                    totalCount = bookPage.totalCount,
                )
        }

        context(_: RaiseContext)
        override suspend fun getAuthorsFeed(): OpdsFeed {
            val updated = now()
            val authors = authorService.getAuthors()
            val entries =
                authors.map { author ->
                    val authorId = author.id.value.toString()
                    OpdsEntry(
                        id = "shelf:author:$authorId",
                        title = author.name,
                        updated = updated,
                        content =
                            OpdsContent(type = "text", value = "${author.ebookCount} e-books"),
                        link =
                            listOf(
                                OpdsLink(
                                    href = links.authorBooks(author.id),
                                    rel = OpdsRel.SUBSECTION,
                                    type = OpdsMimeType.ATOM_ACQUISITION,
                                )
                            ),
                    )
                }
            return feedBuilder(updated)
                .buildFeed(
                    id = "shelf:authors",
                    title = "Authors",
                    selfHref = links.authors(),
                    upHref = links.catalog(),
                    entries = entries,
                )
        }

        context(_: RaiseContext)
        override suspend fun getAuthorBooksFeed(
            authorId: AuthorId,
            page: Int,
            size: Int,
        ): OpdsFeed {
            val updated = now()
            val author = authorService.getAuthor(authorId)
            val bookPage =
                bookService.getBooksByAuthorPage(authorId, page, size, format = BookFormat.EBOOK)
            val entries = bookPage.items.map { mapBookToEntry(it, updated) }

            return feedBuilder(updated)
                .buildFeed(
                    id = "shelf:author:${authorId.value}",
                    title = "Books by ${author.name}",
                    selfHref = links.authorBooks(authorId, page, size),
                    upHref = links.authors(),
                    type = OpdsMimeType.ATOM_ACQUISITION,
                    entries = entries,
                    page = page,
                    size = size,
                    totalCount = bookPage.totalCount,
                )
        }

        context(_: RaiseContext)
        override suspend fun getSeriesFeed(): OpdsFeed {
            val updated = now()
            val seriesList = seriesService.getSeries()
            val entries =
                seriesList.map { series ->
                    val seriesId = series.id.value.toString()
                    OpdsEntry(
                        id = "shelf:series:$seriesId",
                        title = series.name,
                        updated = updated,
                        content =
                            OpdsContent(type = "text", value = "${series.ebookCount} e-books"),
                        link =
                            listOf(
                                OpdsLink(
                                    href = links.seriesBooks(series.id),
                                    rel = OpdsRel.SUBSECTION,
                                    type = OpdsMimeType.ATOM_ACQUISITION,
                                )
                            ),
                    )
                }
            return feedBuilder(updated)
                .buildFeed(
                    id = "shelf:series",
                    title = "Series",
                    selfHref = links.series(),
                    upHref = links.catalog(),
                    entries = entries,
                )
        }

        context(_: RaiseContext)
        override suspend fun getSeriesBooksFeed(
            seriesId: SeriesId,
            page: Int,
            size: Int,
        ): OpdsFeed {
            val updated = now()
            val series = seriesService.getSeries(seriesId)
            val bookPage =
                bookService.getBooksBySeriesPage(seriesId, page, size, format = BookFormat.EBOOK)
            val entries = bookPage.items.map { mapBookToEntry(it, updated) }

            return feedBuilder(updated)
                .buildFeed(
                    id = "shelf:series:${seriesId.value}",
                    title = "Series: ${series.name}",
                    selfHref = links.seriesBooks(seriesId, page, size),
                    upHref = links.series(),
                    type = OpdsMimeType.ATOM_ACQUISITION,
                    entries = entries,
                    page = page,
                    size = size,
                    totalCount = bookPage.totalCount,
                )
        }

        context(_: RaiseContext)
        override suspend fun getOpenSearchDescription(): OpenSearchDescription {
            return OpenSearchDescription(
                ShortName = "Shelf",
                Description = "Search the Shelf e-book catalog",
                Url =
                    listOf(
                        OpenSearchUrl(
                            type = OpdsMimeType.ATOM_ACQUISITION,
                            template = "${links.search()}?q={searchTerms}",
                        )
                    ),
            )
        }

        context(_: RaiseContext)
        override suspend fun searchBooksFeed(query: String, page: Int, size: Int): OpdsFeed {
            val updated = now()
            val searchResult = searchService.search(query)

            // Apply simple manual pagination to the search results
            val allBooks = searchResult.books
            val totalCount = allBooks.size.toLong()
            val pagedBooks = allBooks.drop(page * size).take(size)

            val bookIds = pagedBooks.map { it.id }
            val aggregates = bookService.getBooksAggregates(bookIds)

            val entries = aggregates.map { aggregate -> mapBookToEntry(aggregate, updated) }

            return feedBuilder(updated)
                .buildFeed(
                    id = "shelf:search:$query",
                    title = "Search results for: $query",
                    selfHref = "${links.search()}?q=$query",
                    upHref = links.catalog(),
                    type = OpdsMimeType.ATOM_ACQUISITION,
                    entries = entries,
                    page = page,
                    size = size,
                    totalCount = totalCount,
                )
        }
    }
