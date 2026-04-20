@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import app.cash.sqldelight.Query
import app.cash.sqldelight.Query.Listener
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.book.domain.BookAlreadyExists
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookNotFound
import io.tarantini.shelf.catalog.book.domain.BookRoot
import io.tarantini.shelf.catalog.book.domain.BookSummary
import io.tarantini.shelf.catalog.book.domain.SavedBookRoot
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.book.persistence.Books
import io.tarantini.shelf.catalog.book.persistence.SelectBooksForAuthors
import io.tarantini.shelf.catalog.book.persistence.SelectBooksForLibrary
import io.tarantini.shelf.catalog.book.persistence.SelectBooksForSeries
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.organization.library.domain.LibraryId
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

context(_: RaiseContext)
fun BookQueries.getAllBooks(): List<SavedBookRoot> =
    selectAll().executeAsList().map { it.toDomain() }

context(_: RaiseContext)
fun BookQueries.getBookById(id: BookId): SavedBookRoot =
    selectById(id).executeAsOneOrNull()?.toDomain() ?: raise(BookNotFound)

context(_: RaiseContext)
fun BookQueries.getBooksByIds(ids: List<BookId>): List<SavedBookRoot> {
    if (ids.isEmpty()) return emptyList()
    return selectByIds(ids).executeAsList().map { it.toDomain() }
}

context(_: RaiseContext)
fun BookQueries.linkSeries(id: BookId, seriesId: SeriesId, index: Double) =
    insertBookSeries(id, seriesId, index)

context(_: RaiseContext)
fun BookQueries.getBooksForAuthors(authorIds: List<AuthorId>): Map<AuthorId, List<SavedBookRoot>> {
    if (authorIds.isEmpty()) return emptyMap()
    return selectBooksForAuthors(authorIds).executeAsList().groupBy({ it.author }) { it.toDomain() }
}

context(_: RaiseContext)
fun BookQueries.getBooksForSeries(seriesIds: List<SeriesId>): Map<SeriesId, List<SavedBookRoot>> {
    if (seriesIds.isEmpty()) return emptyMap()
    return selectBooksForSeries(seriesIds).executeAsList().groupBy({ it.series }) { it.toDomain() }
}

context(_: RaiseContext)
fun BookQueries.getBooksForLibrary(
    libraryIds: List<LibraryId>
): Map<LibraryId, List<SavedBookRoot>> {
    if (libraryIds.isEmpty()) return emptyMap()
    return selectBooksForLibrary(libraryIds).executeAsList().groupBy({ it.library }) {
        it.toDomain()
    }
}

context(_: RaiseContext)
fun BookQueries.createBook(title: String, coverPath: StoragePath?): BookId {
    return insert(title, coverPath).executeAsOneOrNull() ?: raise(BookAlreadyExists)
}

context(driver: SqlDriver)
fun BookQueries.fuzzySearch(name: String): Query<BookSummary> {
    val sql =
        """
        WITH search AS (SELECT ?::text AS val)
        SELECT 
            id, 
            title, 
            cover_path,
            -- We calculate both for ranking
            similarity(title, search.val) AS total_score,
            word_similarity(search.val, title) AS word_score
        FROM books, search
        WHERE 
            -- Show results that pass EITHER threshold
            similarity(title, search.val) > 0.3
            OR search.val <% title -- The word_similarity operator
        ORDER BY 
            word_score DESC,   -- Prioritize finding the exact phrase/word
            total_score DESC,  -- Then prioritize the "cleanest" overall match
            title ASC
        LIMIT 20;
        """
            .trimIndent()

    return object :
        Query<BookSummary>({ cursor ->
            BookSummary(
                id = BookId.fromRaw(cursor.getString(0)!!),
                title = cursor.getString(1)!!,
                coverPath = cursor.getString(2)?.let { StoragePath.fromRaw(it) },
                authorNames = emptyList(),
                seriesName = null,
                seriesIndex = null,
            )
        }) {
        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
            return driver.executeQuery(null, sql, mapper, 1) { bindString(0, name) }
        }

        override fun addListener(listener: Listener) {
            driver.addListener("fuzzySearch", listener = listener)
        }

        override fun removeListener(listener: Listener) {
            driver.removeListener("fuzzySearch", listener = listener)
        }
    }
}

private fun Books.toDomain(): SavedBookRoot =
    BookRoot.fromRaw(id = id, title = title, coverPath = cover_path)

private fun SelectBooksForAuthors.toDomain(): SavedBookRoot =
    BookRoot.fromRaw(id = id, title = title, coverPath = cover_path)

private fun SelectBooksForSeries.toDomain(): SavedBookRoot =
    BookRoot.fromRaw(id = id, title = title, coverPath = cover_path)

private fun SelectBooksForLibrary.toDomain(): SavedBookRoot =
    BookRoot.fromRaw(id = id, title = title, coverPath = cover_path)
