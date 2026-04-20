package io.tarantini.shelf.catalog.search

import app.cash.sqldelight.db.SqlDriver
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.fuzzySearch
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.book.fuzzySearch
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.search.domain.GlobalSearchResult
import io.tarantini.shelf.catalog.series.fuzzySearch
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

interface SearchService {
    context(_: RaiseContext, _: SqlDriver)
    suspend fun search(query: String): GlobalSearchResult
}

fun searchService(
    bookQueries: BookQueries,
    authorQueries: AuthorQueries,
    seriesQueries: SeriesQueries,
): SearchService = CatalogSearchService(bookQueries, authorQueries, seriesQueries)

private class CatalogSearchService(
    private val bookQueries: BookQueries,
    private val authorQueries: AuthorQueries,
    private val seriesQueries: SeriesQueries,
) : SearchService {
    context(_: RaiseContext, _: SqlDriver)
    override suspend fun search(query: String): GlobalSearchResult =
        withContext(Dispatchers.IO) {
            val books = async { bookQueries.fuzzySearch(query).executeAsList() }

            val authors = async { authorQueries.fuzzySearch(query).executeAsList() }

            val series = async { seriesQueries.fuzzySearch(query).executeAsList() }

            GlobalSearchResult(
                books = books.await(),
                authors = authors.await(),
                series = series.await(),
            )
        }
}
