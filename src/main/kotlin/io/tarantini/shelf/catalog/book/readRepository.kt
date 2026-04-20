package io.tarantini.shelf.catalog.book

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookRoot
import io.tarantini.shelf.catalog.book.domain.SavedBookRoot
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import io.tarantini.shelf.organization.library.domain.LibraryId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BookReadRepository {
    context(_: RaiseContext)
    suspend fun getBook(id: BookId): SavedBookRoot

    context(_: RaiseContext)
    suspend fun getAllBooks(): List<SavedBookRoot>

    context(_: RaiseContext)
    suspend fun getBooksPage(
        page: Int,
        size: Int,
        sortBy: String?,
        sortDir: String?,
        format: BookFormat?,
    ): List<SavedBookRoot>

    context(_: RaiseContext)
    suspend fun countBooks(format: BookFormat?): Long

    context(_: RaiseContext)
    suspend fun getBooksByAuthorPage(
        authorId: AuthorId,
        page: Int,
        size: Int,
        format: BookFormat?,
    ): List<SavedBookRoot>

    context(_: RaiseContext)
    suspend fun countBooksForAuthor(authorId: AuthorId, format: BookFormat?): Long

    context(_: RaiseContext)
    suspend fun getBooksBySeriesPage(
        seriesId: SeriesId,
        page: Int,
        size: Int,
        format: BookFormat?,
    ): List<SavedBookRoot>

    context(_: RaiseContext)
    suspend fun countBooksForSeries(seriesId: SeriesId, format: BookFormat?): Long

    context(_: RaiseContext)
    suspend fun getBooksByIds(ids: List<BookId>): List<SavedBookRoot>

    context(_: RaiseContext)
    suspend fun getBooksForAuthors(authorIds: List<AuthorId>): Map<AuthorId, List<SavedBookRoot>>

    context(_: RaiseContext)
    suspend fun getBooksForSeries(seriesIds: List<SeriesId>): Map<SeriesId, List<SavedBookRoot>>

    context(_: RaiseContext)
    suspend fun getBooksForLibraries(
        libraryIds: List<LibraryId>
    ): Map<LibraryId, List<SavedBookRoot>>
}

fun bookReadRepository(
    bookQueries: BookQueries,
    authorQueries: AuthorQueries,
    seriesQueries: SeriesQueries,
): BookReadRepository =
    object : BookReadRepository {
        context(_: RaiseContext)
        override suspend fun getBook(id: BookId): SavedBookRoot =
            withContext(Dispatchers.IO) { bookQueries.getBookById(id) }

        context(_: RaiseContext)
        override suspend fun getAllBooks(): List<SavedBookRoot> =
            withContext(Dispatchers.IO) {
                bookQueries.selectAll().executeAsList().map {
                    BookRoot.fromRaw(it.id, it.title, it.cover_path)
                }
            }

        context(_: RaiseContext)
        override suspend fun getBooksPage(
            page: Int,
            size: Int,
            sortBy: String?,
            sortDir: String?,
            format: BookFormat?,
        ): List<SavedBookRoot> =
            withContext(Dispatchers.IO) {
                val limit = size.toLong()
                val offset = (page * size).toLong()
                val actualSortBy = sortBy ?: "createdAt"
                val actualSortDir = sortDir ?: "DESC"

                val query =
                    if (format != null) {
                        bookQueries.selectBooksByFormatPage(
                            format = format,
                            sortBy = actualSortBy,
                            sortDir = actualSortDir,
                            limit = limit,
                            offset = offset,
                        )
                    } else {
                        bookQueries.selectBooksPage(
                            sortBy = actualSortBy,
                            sortDir = actualSortDir,
                            limit = limit,
                            offset = offset,
                        )
                    }

                query.executeAsList().map { BookRoot.fromRaw(it.id, it.title, it.cover_path) }
            }

        context(_: RaiseContext)
        override suspend fun countBooks(format: BookFormat?): Long =
            withContext(Dispatchers.IO) {
                if (format != null) bookQueries.countBooksByFormat(format).executeAsOne()
                else bookQueries.countAll().executeAsOne()
            }

        context(_: RaiseContext)
        override suspend fun getBooksByAuthorPage(
            authorId: AuthorId,
            page: Int,
            size: Int,
            format: BookFormat?,
        ): List<SavedBookRoot> =
            withContext(Dispatchers.IO) {
                val limit = size.toLong()
                val offset = (page * size).toLong()
                if (format != null) {
                    authorQueries
                        .selectBooksForAuthorByFormatPage(authorId, format, limit, offset)
                        .executeAsList()
                        .map { BookRoot.fromRaw(it.id, it.title, it.cover_path) }
                } else {
                    authorQueries
                        .selectBooksForAuthorPage(authorId, limit, offset)
                        .executeAsList()
                        .map { BookRoot.fromRaw(it.id, it.title, it.cover_path) }
                }
            }

        context(_: RaiseContext)
        override suspend fun countBooksForAuthor(authorId: AuthorId, format: BookFormat?): Long =
            withContext(Dispatchers.IO) {
                if (format != null)
                    authorQueries.countBooksForAuthorByFormat(authorId, format).executeAsOne()
                else authorQueries.countBooksForAuthor(authorId).executeAsOne()
            }

        context(_: RaiseContext)
        override suspend fun getBooksBySeriesPage(
            seriesId: SeriesId,
            page: Int,
            size: Int,
            format: BookFormat?,
        ): List<SavedBookRoot> =
            withContext(Dispatchers.IO) {
                val limit = size.toLong()
                val offset = (page * size).toLong()
                if (format != null) {
                    seriesQueries
                        .selectBooksForSeriesByFormatPage(seriesId, format, limit, offset)
                        .executeAsList()
                        .map { BookRoot.fromRaw(it.id, it.title, it.cover_path) }
                } else {
                    seriesQueries
                        .selectBooksForSeriesPage(seriesId, limit, offset)
                        .executeAsList()
                        .map { BookRoot.fromRaw(it.id, it.title, it.cover_path) }
                }
            }

        context(_: RaiseContext)
        override suspend fun countBooksForSeries(seriesId: SeriesId, format: BookFormat?): Long =
            withContext(Dispatchers.IO) {
                if (format != null)
                    seriesQueries.countBooksForSeriesByFormat(seriesId, format).executeAsOne()
                else seriesQueries.countBooksForSeries(seriesId).executeAsOne()
            }

        context(_: RaiseContext)
        override suspend fun getBooksByIds(ids: List<BookId>): List<SavedBookRoot> =
            withContext(Dispatchers.IO) {
                if (ids.isEmpty()) return@withContext emptyList()
                bookQueries.selectByIds(ids).executeAsList().map {
                    BookRoot.fromRaw(it.id, it.title, it.cover_path)
                }
            }

        context(_: RaiseContext)
        override suspend fun getBooksForAuthors(
            authorIds: List<AuthorId>
        ): Map<AuthorId, List<SavedBookRoot>> =
            withContext(Dispatchers.IO) { bookQueries.getBooksForAuthors(authorIds) }

        context(_: RaiseContext)
        override suspend fun getBooksForSeries(
            seriesIds: List<SeriesId>
        ): Map<SeriesId, List<SavedBookRoot>> =
            withContext(Dispatchers.IO) { bookQueries.getBooksForSeries(seriesIds) }

        context(_: RaiseContext)
        override suspend fun getBooksForLibraries(
            libraryIds: List<LibraryId>
        ): Map<LibraryId, List<SavedBookRoot>> =
            withContext(Dispatchers.IO) { bookQueries.getBooksForLibrary(libraryIds) }
    }
