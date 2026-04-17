package io.tarantini.shelf.catalog.search

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorSummary
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.book.domain.BookSummary
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.search.domain.GlobalSearchResult
import io.tarantini.shelf.catalog.series.domain.SeriesSummary
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

interface SearchService {
    context(_: RaiseContext)
    suspend fun search(query: String): GlobalSearchResult
}

fun searchService(
    bookQueries: BookQueries,
    authorQueries: AuthorQueries,
    seriesQueries: SeriesQueries,
) =
    object : SearchService {
        context(_: RaiseContext)
        override suspend fun search(query: String): GlobalSearchResult =
            withContext(Dispatchers.IO) {
                val books = async {
                    bookQueries.searchBooks(query).executeAsList().map {
                        BookSummary(
                            id = it.id,
                            title = it.title,
                            coverPath = it.cover_path,
                            authorNames = emptyList(),
                            seriesName = null,
                            seriesIndex = null,
                        )
                    }
                }

                val authors = async {
                    authorQueries.searchAuthors(query).executeAsList().map {
                        AuthorSummary(id = it.id, name = it.name, bookCount = it.bookCount.toInt())
                    }
                }

                val series = async {
                    seriesQueries.searchSeries(query).executeAsList().map {
                        SeriesSummary(id = it.id, name = it.title, bookCount = it.bookCount.toInt())
                    }
                }

                GlobalSearchResult(
                    books = books.await(),
                    authors = authors.await(),
                    series = series.await(),
                )
            }
    }
