@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.author

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.domain.AuthorNotFound
import io.tarantini.shelf.catalog.author.domain.AuthorPage
import io.tarantini.shelf.catalog.author.domain.AuthorRoot
import io.tarantini.shelf.catalog.author.domain.AuthorSummary
import io.tarantini.shelf.catalog.author.domain.SavedAuthorRoot
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.author.persistence.AuthorSummaries
import io.tarantini.shelf.catalog.author.persistence.SelectAuthorsForBooks
import io.tarantini.shelf.catalog.author.persistence.SelectAuthorsForSeries
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

context(_: RaiseContext)
fun AuthorQueries.getAllAuthors(): List<AuthorSummary> =
    selectAll().executeAsList().map { it.toSummary() }

context(_: RaiseContext)
fun AuthorQueries.getAuthorsPage(
    page: Int,
    size: Int,
    sortBy: String,
    sortDir: String,
): AuthorPage {
    val limit = size.toLong()
    val offset = (page * size).toLong()
    val items =
        selectAuthorsPage(sortBy, sortDir, limit, offset).executeAsList().map { it.toSummary() }
    val totalCount = countAllAuthors().executeAsOne()
    return AuthorPage(items = items, totalCount = totalCount, page = page, size = size)
}

context(_: RaiseContext)
fun AuthorQueries.getAuthorById(authorId: AuthorId): SavedAuthorRoot =
    selectById(authorId).executeAsOneOrNull()?.toRoot() ?: raise(AuthorNotFound)

context(_: RaiseContext)
fun AuthorQueries.getAuthorByName(fullName: String): SavedAuthorRoot =
    selectByName(fullName).executeAsOneOrNull()?.toRoot() ?: raise(AuthorNotFound)

context(_: RaiseContext)
fun AuthorQueries.createAuthor(name: String): AuthorId = insert(name).executeAsOne()

context(_: RaiseContext)
fun AuthorQueries.updateAuthor(id: AuthorId, name: String): AuthorId =
    update(id = id, name = name).executeAsOneOrNull() ?: raise(AuthorNotFound)

context(_: RaiseContext)
fun AuthorQueries.updateAuthorImagePath(id: AuthorId, imagePath: StoragePath?): AuthorId =
    updateImagePath(id = id, imagePath = imagePath?.value).executeAsOneOrNull()
        ?: raise(AuthorNotFound)

context(_: RaiseContext)
fun AuthorQueries.deleteAuthor(id: AuthorId): AuthorId =
    deleteById(id).executeAsOneOrNull() ?: raise(AuthorNotFound)

context(_: RaiseContext)
fun AuthorQueries.linkBook(authorId: AuthorId, bookId: BookId) = insertBookAuthor(bookId, authorId)

context(_: RaiseContext)
fun AuthorQueries.getAuthorsForBooks(bookIds: List<BookId>): Map<BookId, List<SavedAuthorRoot>> {
    if (bookIds.isEmpty()) return emptyMap()
    return selectAuthorsForBooks(bookIds).executeAsList().groupBy({ it.book }) { it.toRoot() }
}

context(_: RaiseContext)
fun AuthorQueries.getAuthorsForSeries(
    seriesIds: List<SeriesId>
): Map<SeriesId, List<SavedAuthorRoot>> {
    if (seriesIds.isEmpty()) return emptyMap()
    return selectAuthorsForSeries(seriesIds).executeAsList().groupBy({ it.series }) { it.toRoot() }
}

context(driver: SqlDriver)
fun AuthorQueries.fuzzySearch(name: String): Query<AuthorSummary> {
    val sql =
        """
        WITH search AS (SELECT ?::text AS val)
        SELECT 
            id, 
            name, 
            imagePath,
            bookCount,
            -- We calculate both for ranking
            similarity(name, search.val) AS total_score,
            word_similarity(search.val, name) AS word_score
        FROM authorSummaries, search
        WHERE 
            -- Show results that pass EITHER threshold
            similarity(name, search.val) > 0.3
            OR search.val <% name -- The word_similarity operator
        ORDER BY 
            word_score DESC,   -- Prioritize finding the exact phrase/word
            total_score DESC,  -- Then prioritize the "cleanest" overall match
            name ASC
        LIMIT 20;
        """
            .trimIndent()

    return object :
        Query<AuthorSummary>({ cursor ->
            AuthorSummary(
                id = AuthorId.fromRaw(cursor.getString(0)!!),
                name = cursor.getString(1)!!,
                bookCount = cursor.getLong(3)!!.toInt(),
                imagePath = cursor.getString(2),
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

private fun AuthorSummaries.toSummary() =
    AuthorSummary(id, name, bookCount.toInt(), ebookCount.toInt(), imagePath)

private fun AuthorSummaries.toRoot() =
    AuthorRoot.fromRaw(id, name, imagePath?.let { StoragePath.fromRaw(it) })

private fun SelectAuthorsForBooks.toRoot() = AuthorRoot.fromRaw(id, name)

private fun SelectAuthorsForSeries.toRoot() = AuthorRoot.fromRaw(id, name)
