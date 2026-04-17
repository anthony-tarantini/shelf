@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.series

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookSeriesEntry
import io.tarantini.shelf.catalog.book.getBooksForSeries
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.series.domain.*
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SeriesProvider {
    context(_: RaiseContext)
    suspend fun getSeriesPage(
        page: Int = 0,
        size: Int = 20,
        sortBy: String = "title",
        sortDir: String = "ASC",
    ): SeriesPage

    context(_: RaiseContext)
    suspend fun getSeries(): List<SeriesSummary>

    context(_: RaiseContext)
    suspend fun getSeries(id: SeriesId): SavedSeriesRoot

    context(_: RaiseContext)
    suspend fun getSeriesAggregate(id: SeriesId): SavedSeriesAggregate

    context(_: RaiseContext)
    suspend fun getSeriesByName(name: String): List<SavedSeriesRoot>

    context(_: RaiseContext)
    suspend fun searchSeriesFuzzy(name: String): List<SeriesSummary>

    context(_: RaiseContext)
    suspend fun getPreferredCoverPath(id: SeriesId): StoragePath
}

interface BookSeriesProvider {
    context(_: RaiseContext)
    suspend fun getSeriesForBooks(booksIds: List<BookId>): Map<BookId, List<SavedSeriesRoot>>

    context(_: RaiseContext)
    suspend fun getBookSeriesEntries(bookIds: List<BookId>): Map<BookId, List<BookSeriesEntry>>
}

interface AuthorSeriesProvider {
    context(_: RaiseContext)
    suspend fun getSeriesForAuthors(authorIds: List<AuthorId>): Map<AuthorId, List<SavedSeriesRoot>>
}

interface SeriesModifier {
    context(_: RaiseContext)
    suspend fun createSeries(title: String): SavedSeriesRoot

    context(_: RaiseContext)
    suspend fun updateSeries(id: SeriesId, title: String?): SavedSeriesRoot

    context(_: RaiseContext)
    suspend fun deleteSeries(id: SeriesId): SeriesId

    context(_: RaiseContext)
    suspend fun linkBook(seriesId: SeriesId, bookId: BookId, index: Double? = null)
}

interface SeriesService : SeriesProvider, BookSeriesProvider, AuthorSeriesProvider, SeriesModifier

fun seriesService(
    seriesQueries: SeriesQueries,
    bookQueries: BookQueries,
    storageService: StorageService,
) =
    object : SeriesService {
        context(_: RaiseContext)
        override suspend fun getSeriesPage(page: Int, size: Int, sortBy: String, sortDir: String) =
            withContext(Dispatchers.IO) { seriesQueries.getSeriesPage(page, size, sortBy, sortDir) }

        context(_: RaiseContext)
        override suspend fun getSeries() =
            withContext(Dispatchers.IO) {
                val series = seriesQueries.getAllSeries()
                val coverPaths = seriesQueries.getCoverPaths(series.map { it.id })
                series.map { summary -> summary.copy(coverPath = coverPaths[summary.id]) }
            }

        context(_: RaiseContext)
        override suspend fun getSeries(id: SeriesId) =
            withContext(Dispatchers.IO) {
                val series = seriesQueries.getSeriesById(id)
                val coverPath = seriesQueries.getCoverPaths(listOf(id))[id]
                SeriesRoot.fromRaw(id, series.name, coverPath)
            }

        context(_: RaiseContext)
        override suspend fun getSeriesAggregate(id: SeriesId) =
            withContext(Dispatchers.IO) {
                val series = seriesQueries.getSeriesById(id)
                val coverPath = seriesQueries.getCoverPaths(listOf(id))[id]
                SeriesAggregate(
                    series = SeriesRoot.fromRaw(id, series.name, coverPath),
                    books = bookQueries.getBooksForSeries(listOf(id)).getOrDefault(id, emptyList()),
                )
            }

        context(_: RaiseContext)
        override suspend fun getSeriesByName(name: String): List<SavedSeriesRoot> =
            withContext(Dispatchers.IO) {
                seriesQueries
                    .selectByTitle(
                        title = name,
                        mapper = { id, title, bookCount, ebookCount, authorCount ->
                            mapSeriesRootForSearch(id, title, bookCount, ebookCount, authorCount)
                        },
                    )
                    .executeAsList()
            }

        context(_: RaiseContext)
        override suspend fun searchSeriesFuzzy(name: String): List<SeriesSummary> =
            withContext(Dispatchers.IO) {
                throw NotImplementedError("Fuzzy search for series is temporarily disabled")
            }

        context(_: RaiseContext)
        override suspend fun getPreferredCoverPath(id: SeriesId): StoragePath =
            withContext(Dispatchers.IO) {
                val coverPath =
                    seriesQueries.getCoverPaths(listOf(id))[id] ?: raise(SeriesCoverNotFound)
                if (storageService.exists(coverPath.thumbnail())) coverPath.thumbnail()
                else coverPath
            }

        context(_: RaiseContext)
        override suspend fun getSeriesForBooks(booksIds: List<BookId>) =
            withContext(Dispatchers.IO) { seriesQueries.getSeriesForBooks(booksIds) }

        context(_: RaiseContext)
        override suspend fun getBookSeriesEntries(bookIds: List<BookId>) =
            withContext(Dispatchers.IO) { seriesQueries.getBookSeriesEntries(bookIds) }

        context(_: RaiseContext)
        override suspend fun getSeriesForAuthors(authorIds: List<AuthorId>) =
            withContext(Dispatchers.IO) { seriesQueries.getSeriesForAuthors(authorIds) }

        context(_: RaiseContext)
        override suspend fun createSeries(title: String) =
            withContext(Dispatchers.IO) {
                seriesQueries.transactionWithResult {
                    val id = seriesQueries.insert(title).executeAsOne()
                    seriesQueries.getSeriesById(id)
                }
            }

        context(_: RaiseContext)
        override suspend fun updateSeries(id: SeriesId, title: String?): SavedSeriesRoot =
            withContext(Dispatchers.IO) {
                seriesQueries.transactionWithResult {
                    val existing = seriesQueries.getSeriesById(id)
                    seriesQueries.updateSeries(title ?: existing.name, id)
                    seriesQueries.getSeriesById(id)
                }
            }

        context(_: RaiseContext)
        override suspend fun deleteSeries(id: SeriesId) =
            withContext(Dispatchers.IO) {
                seriesQueries.transactionWithResult {
                    seriesQueries.deleteSeriesAuthors(id)
                    seriesQueries.deleteSeriesBooks(id)
                    seriesQueries.deleteById(id).executeAsOne()
                }
            }

        context(_: RaiseContext)
        override suspend fun linkBook(seriesId: SeriesId, bookId: BookId, index: Double?) {
            withContext(Dispatchers.IO) { seriesQueries.insertSeriesBook(seriesId, bookId, index) }
        }
    }

private fun mapSeriesRootForSearch(
    id: SeriesId,
    title: String,
    bookCount: Long,
    ebookCount: Long,
    authorCount: Long,
): SavedSeriesRoot = SeriesRoot.fromRaw(id, title)
