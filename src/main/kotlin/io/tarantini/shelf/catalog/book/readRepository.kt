@file:OptIn(ExperimentalUuidApi::class)

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
import io.tarantini.shelf.user.activity.domain.ReadStatus
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FILTER_FALLBACK_USER_ID =
    UserId.fromRaw(Uuid.parse("00000000-0000-0000-0000-000000000000"))

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
        titleQuery: String?,
        authorQuery: String?,
        seriesQuery: String?,
        status: ReadStatus?,
        format: BookFormat?,
        userId: UserId?,
    ): List<SavedBookRoot>

    context(_: RaiseContext)
    suspend fun countBooks(
        userId: UserId?,
        titleQuery: String?,
        authorQuery: String?,
        seriesQuery: String?,
        status: ReadStatus?,
        format: BookFormat?,
    ): Long

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
            titleQuery: String?,
            authorQuery: String?,
            seriesQuery: String?,
            status: ReadStatus?,
            format: BookFormat?,
            userId: UserId?,
        ): List<SavedBookRoot> =
            withContext(Dispatchers.IO) {
                val limit = size.toLong()
                val offset = (page * size).toLong()
                val actualSortBy = sortBy ?: "createdAt"
                val actualSortDir = sortDir ?: "DESC"
                val applyStatus = if (status != null) 1 else 0
                val applyFormat = if (format != null) 1 else 0
                val effectiveStatus = status?.name ?: ReadStatus.UNREAD.name
                val effectiveFormat = format ?: BookFormat.EBOOK
                val effectiveUserId = userId ?: FILTER_FALLBACK_USER_ID

                val query =
                    bookQueries.selectFilteredBooksPage(
                        applyFormat = applyFormat,
                        format = effectiveFormat,
                        applyStatus = applyStatus,
                        status = effectiveStatus,
                        userId = effectiveUserId,
                        titleQuery = titleQuery,
                        authorQuery = authorQuery,
                        seriesQuery = seriesQuery,
                        sortBy = actualSortBy,
                        sortDir = actualSortDir,
                        limit = limit,
                        offset = offset,
                    )

                query.executeAsList().map { BookRoot.fromRaw(it.id, it.title, it.cover_path) }
            }

        context(_: RaiseContext)
        override suspend fun countBooks(
            userId: UserId?,
            titleQuery: String?,
            authorQuery: String?,
            seriesQuery: String?,
            status: ReadStatus?,
            format: BookFormat?,
        ): Long =
            withContext(Dispatchers.IO) {
                val applyStatus = if (status != null) 1 else 0
                val applyFormat = if (format != null) 1 else 0
                val effectiveStatus = status?.name ?: ReadStatus.UNREAD.name
                val effectiveFormat = format ?: BookFormat.EBOOK
                val effectiveUserId = userId ?: FILTER_FALLBACK_USER_ID
                bookQueries
                    .countFilteredBooks(
                        applyFormat = applyFormat,
                        format = effectiveFormat,
                        applyStatus = applyStatus,
                        status = effectiveStatus,
                        userId = effectiveUserId,
                        titleQuery = titleQuery,
                        authorQuery = authorQuery,
                        seriesQuery = seriesQuery,
                    )
                    .executeAsOne()
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
