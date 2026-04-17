@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.author

import app.cash.sqldelight.db.SqlDriver
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.domain.*
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.getBooksForAuthors
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AuthorProvider {
    context(_: RaiseContext)
    suspend fun getAuthorsPage(
        page: Int = 0,
        size: Int = 20,
        sortBy: String = "name",
        sortDir: String = "ASC",
    ): AuthorPage

    context(_: RaiseContext)
    suspend fun getAuthors(): List<AuthorSummary>

    context(_: RaiseContext)
    suspend fun getAuthor(id: AuthorId): SavedAuthorRoot

    context(_: RaiseContext)
    suspend fun getAuthorAggregate(id: AuthorId): SavedAuthorAggregate

    context(_: RaiseContext)
    suspend fun getAuthorByFullName(name: String): List<SavedAuthorRoot>

    context(_: RaiseContext)
    suspend fun searchAuthorFuzzy(name: String): List<AuthorSummary>
}

interface BookAuthorProvider {
    context(_: RaiseContext)
    suspend fun getAuthorsForBooks(bookIds: List<BookId>): Map<BookId, List<SavedAuthorRoot>>
}

interface SeriesAuthorProvider {
    context(_: RaiseContext)
    suspend fun getAuthorsForSeries(seriesIds: List<SeriesId>): Map<SeriesId, List<SavedAuthorRoot>>
}

interface AuthorModifier {
    context(_: RaiseContext)
    suspend fun createAuthor(author: NewAuthorRoot): SavedAuthorRoot

    context(_: RaiseContext)
    suspend fun updateAuthor(author: AuthorRootUpdate): SavedAuthorRoot

    context(_: RaiseContext)
    suspend fun updateAuthorImage(id: AuthorId, imagePath: StoragePath?): AuthorId

    context(_: RaiseContext)
    suspend fun deleteAuthor(id: AuthorId): AuthorId

    context(_: RaiseContext)
    suspend fun linkBook(authorId: AuthorId, bookId: BookId)
}

interface AuthorService : AuthorProvider, BookAuthorProvider, SeriesAuthorProvider, AuthorModifier

fun authorService(sqlDriver: SqlDriver, authorQueries: AuthorQueries, bookQueries: BookQueries) =
    object : AuthorService {
        context(_: RaiseContext)
        override suspend fun getAuthorsPage(page: Int, size: Int, sortBy: String, sortDir: String) =
            withContext(Dispatchers.IO) {
                authorQueries.getAuthorsPage(page, size, sortBy, sortDir)
            }

        context(_: RaiseContext)
        override suspend fun getAuthors() =
            withContext(Dispatchers.IO) { authorQueries.getAllAuthors() }

        context(_: RaiseContext)
        override suspend fun getAuthor(id: AuthorId) =
            withContext(Dispatchers.IO) { authorQueries.getAuthorById(id) }

        context(_: RaiseContext)
        override suspend fun getAuthorAggregate(id: AuthorId) =
            withContext(Dispatchers.IO) {
                AuthorAggregate(
                    author = authorQueries.getAuthorById(id),
                    books = bookQueries.getBooksForAuthors(listOf(id)).getOrDefault(id, emptyList()),
                )
            }

        context(_: RaiseContext)
        override suspend fun getAuthorByFullName(name: String): List<SavedAuthorRoot> =
            withContext(Dispatchers.IO) {
                authorQueries.selectByName(name).executeAsList().map { summary ->
                    AuthorRoot.fromRaw(summary.id, summary.name)
                }
            }

        context(_: RaiseContext)
        override suspend fun searchAuthorFuzzy(name: String): List<AuthorSummary> =
            withContext(Dispatchers.IO) {
                with(sqlDriver) { authorQueries.fuzzySearch(name).executeAsList() }
            }

        context(_: RaiseContext)
        override suspend fun getAuthorsForBooks(bookIds: List<BookId>) =
            withContext(Dispatchers.IO) { authorQueries.getAuthorsForBooks(bookIds) }

        context(_: RaiseContext)
        override suspend fun getAuthorsForSeries(seriesIds: List<SeriesId>) =
            withContext(Dispatchers.IO) { authorQueries.getAuthorsForSeries(seriesIds) }

        context(_: RaiseContext)
        override suspend fun createAuthor(author: NewAuthorRoot) =
            withContext(Dispatchers.IO) {
                authorQueries.transactionWithResult {
                    authorQueries.createAuthor(author.name).let { authorQueries.getAuthorById(it) }
                }
            }

        context(_: RaiseContext)
        override suspend fun updateAuthor(author: AuthorRootUpdate) =
            withContext(Dispatchers.IO) {
                authorQueries.transactionWithResult {
                    authorQueries.updateAuthor(author.id.id, author.name)
                    authorQueries.getAuthorById(author.id.id)
                }
            }

        context(_: RaiseContext)
        override suspend fun updateAuthorImage(id: AuthorId, imagePath: StoragePath?) =
            withContext(Dispatchers.IO) { authorQueries.updateAuthorImagePath(id, imagePath) }

        context(_: RaiseContext)
        override suspend fun deleteAuthor(id: AuthorId) =
            withContext(Dispatchers.IO) { authorQueries.deleteAuthor(id) }

        context(_: RaiseContext)
        override suspend fun linkBook(authorId: AuthorId, bookId: BookId) {
            withContext(Dispatchers.IO) { authorQueries.linkBook(authorId, bookId) }
        }
    }
