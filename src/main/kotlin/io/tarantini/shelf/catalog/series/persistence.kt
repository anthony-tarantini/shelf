@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.series

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookSeriesEntry
import io.tarantini.shelf.catalog.series.domain.*
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

context(_: RaiseContext)
fun SeriesQueries.getAllSeries(): List<SeriesSummary> =
    selectAll(::mapSeriesSummary).executeAsList()

context(_: RaiseContext)
fun SeriesQueries.getSeriesPage(page: Int, size: Int, sortBy: String, sortDir: String): SeriesPage {
    val limit = size.toLong()
    val offset = (page * size).toLong()
    val items = selectSeriesPage(sortBy, sortDir, limit, offset, ::mapSeriesSummary).executeAsList()
    val totalCount = countAllSeries().executeAsOne()
    val coverPaths = getCoverPaths(items.map { it.id })
    val enrichedItems = items.map { it.copy(coverPath = coverPaths[it.id]) }
    return SeriesPage(items = enrichedItems, totalCount = totalCount, page = page, size = size)
}

context(_: RaiseContext)
fun SeriesQueries.getSeriesById(id: SeriesId): SavedSeriesRoot =
    selectById(id, ::mapSeriesRoot).executeAsOneOrNull() ?: raise(SeriesNotFound)

context(_: RaiseContext)
fun SeriesQueries.getSeriesByTitle(title: String) =
    selectByTitle(title, ::mapSeriesRoot).executeAsOneOrNull() ?: raise(SeriesNotFound)

context(_: RaiseContext)
fun SeriesQueries.createSeries(title: String) = insert(title).executeAsOne()

context(_: RaiseContext)
fun SeriesQueries.updateSeries(title: String, id: SeriesId) = update(title, id).executeAsOne()

context(_: RaiseContext)
fun SeriesQueries.getSeriesForAuthors(
    authorIds: List<AuthorId>
): Map<AuthorId, List<SavedSeriesRoot>> {
    if (authorIds.isEmpty()) return emptyMap()
    return selectSeriesForAuthors(authorIds, ::mapSeriesRootWithAuthor).executeAsList().groupBy({
        it.author
    }) {
        it.series
    }
}

context(_: RaiseContext)
fun SeriesQueries.getSeriesForBooks(bookIds: List<BookId>): Map<BookId, List<SavedSeriesRoot>> {
    if (bookIds.isEmpty()) return emptyMap()
    return selectSeriesForBooks(bookIds, ::mapSeriesRootWithBook).executeAsList().groupBy({
        it.book
    }) {
        it.series
    }
}

context(_: RaiseContext)
fun SeriesQueries.getBookSeriesEntries(bookIds: List<BookId>): Map<BookId, List<BookSeriesEntry>> {
    if (bookIds.isEmpty()) return emptyMap()
    return selectSeriesForBooks(bookIds, ::mapBookSeriesEntry).executeAsList().groupBy({
        it.book
    }) {
        it.entry
    }
}

fun SeriesQueries.getCoverPaths(seriesIds: List<SeriesId>): Map<SeriesId, StoragePath> {
    if (seriesIds.isEmpty()) return emptyMap()
    return selectSeriesCoverCandidates(seriesIds) { series, coverPath -> series to coverPath }
        .executeAsList()
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, coverPaths) -> coverPaths.first() }
}

private fun mapSeriesSummary(
    id: SeriesId,
    title: String,
    bookCount: Long,
    ebookCount: Long,
    authorCount: Long,
): SeriesSummary = SeriesSummary(id, title, null, bookCount.toInt(), ebookCount.toInt())

private fun mapSeriesRoot(
    id: SeriesId,
    title: String,
    bookCount: Long,
    ebookCount: Long,
    authorCount: Long,
): SavedSeriesRoot = SeriesRoot.fromRaw(id, title)

private data class SeriesRootWithAuthor(val author: AuthorId, val series: SavedSeriesRoot)

private fun mapSeriesRootWithAuthor(
    author: AuthorId,
    id: SeriesId,
    title: String,
    bookCount: Long,
    ebookCount: Long,
    authorCount: Long,
): SeriesRootWithAuthor = SeriesRootWithAuthor(author, SeriesRoot.fromRaw(id, title))

private data class SeriesRootWithBook(val book: BookId, val series: SavedSeriesRoot)

private fun mapSeriesRootWithBook(
    book: BookId,
    index: Double?,
    id: SeriesId,
    title: String,
    bookCount: Long,
    ebookCount: Long,
    authorCount: Long,
): SeriesRootWithBook = SeriesRootWithBook(book, SeriesRoot.fromRaw(id, title))

private data class BookSeriesEntryWithBook(val book: BookId, val entry: BookSeriesEntry)

private fun mapBookSeriesEntry(
    book: BookId,
    index: Double?,
    id: SeriesId,
    title: String,
    bookCount: Long,
    ebookCount: Long,
    authorCount: Long,
): BookSeriesEntryWithBook = BookSeriesEntryWithBook(book, BookSeriesEntry(id, title, null, index))
