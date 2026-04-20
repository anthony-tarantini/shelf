package io.tarantini.shelf.catalog.book

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookSeriesMutation
import io.tarantini.shelf.catalog.book.domain.canonicalizeBookRelationName
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.series.createSeries
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.getSeriesForAuthors
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries

internal data class ScopedSeriesEntry(val id: SeriesId, val name: String)

internal interface SeriesResolutionStore {
    context(_: RaiseContext)
    fun getScopedSeries(authorIds: List<AuthorId>): Map<AuthorId, List<ScopedSeriesEntry>>

    context(_: RaiseContext)
    fun createSeries(name: String): SeriesId

    context(_: RaiseContext)
    fun linkSeriesToBook(bookId: BookId, seriesId: SeriesId, index: Double)

    context(_: RaiseContext)
    fun linkSeriesToAuthor(seriesId: SeriesId, authorId: AuthorId)

    context(_: RaiseContext)
    fun deleteOrphanedSeries()
}

internal class SqlDelightSeriesResolutionStore(
    private val bookQueries: BookQueries,
    private val seriesQueries: SeriesQueries,
) : SeriesResolutionStore {
    context(_: RaiseContext)
    override fun getScopedSeries(
        authorIds: List<AuthorId>
    ): Map<AuthorId, List<ScopedSeriesEntry>> =
        seriesQueries.getSeriesForAuthors(authorIds).mapValues { (_, seriesRoots) ->
            seriesRoots.map { ScopedSeriesEntry(id = it.id.id, name = it.name) }
        }

    context(_: RaiseContext)
    override fun createSeries(name: String): SeriesId = seriesQueries.createSeries(name)

    context(_: RaiseContext)
    override fun linkSeriesToBook(bookId: BookId, seriesId: SeriesId, index: Double) {
        bookQueries.linkSeries(bookId, seriesId, index)
    }

    context(_: RaiseContext)
    override fun linkSeriesToAuthor(seriesId: SeriesId, authorId: AuthorId) {
        seriesQueries.insertSeriesAuthor(seriesId, authorId)
    }

    context(_: RaiseContext)
    override fun deleteOrphanedSeries() {
        seriesQueries.deleteOrphanedSeries()
    }
}

internal class SqlDelightBookSeriesResolver(private val store: SeriesResolutionStore) :
    BookSeriesResolver {
    context(_: RaiseContext)
    override fun applySeriesMutation(
        bookId: BookId,
        authorIds: List<AuthorId>,
        seriesMutation: BookSeriesMutation.Replace,
    ) {
        val scopedSeries = store.getScopedSeries(authorIds).values.flatten().distinctBy { it.id }
        val scopedSeriesByCanonical =
            scopedSeries.groupBy { seriesRoot -> canonicalizeBookRelationName(seriesRoot.name) }

        seriesMutation.values.forEach { seriesIntent ->
            val canonical = canonicalizeBookRelationName(seriesIntent.name.value)
            val matches = scopedSeriesByCanonical[canonical].orEmpty()
            val resolvedSeriesId =
                if (matches.size == 1) {
                    matches.first().id
                } else {
                    store.createSeries(seriesIntent.name.value)
                }

            store.linkSeriesToBook(bookId, resolvedSeriesId, seriesIntent.index ?: 0.0)
            authorIds.forEach { authorId -> store.linkSeriesToAuthor(resolvedSeriesId, authorId) }
        }
    }

    context(_: RaiseContext)
    override fun deleteOrphanedSeries() {
        store.deleteOrphanedSeries()
    }
}
