@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import arrow.core.raise.context.ensureNotNull
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.BookAuthorProvider
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.book.domain.*
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.MetadataProvider
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.EditionNotFound
import io.tarantini.shelf.catalog.metadata.domain.SavedEdition
import io.tarantini.shelf.catalog.series.BookSeriesProvider
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.organization.library.domain.LibraryId
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BookProvider {
    context(_: RaiseContext)
    suspend fun getBooks(): List<SavedBookRoot>

    context(_: RaiseContext)
    suspend fun getBooksAggregate(): List<SavedBookAggregate>

    context(_: RaiseContext)
    suspend fun getBooksPage(
        page: Int = 0,
        size: Int = 20,
        sortBy: String? = "createdAt",
        sortDir: String? = "DESC",
        format: BookFormat? = null,
    ): BookPage

    context(_: RaiseContext)
    suspend fun getBookSummaries(): List<BookSummary>

    context(_: RaiseContext)
    suspend fun getBook(id: BookId): SavedBookRoot

    context(_: RaiseContext)
    suspend fun getBookAggregate(id: BookId): SavedBookAggregate

    context(_: RaiseContext)
    suspend fun getBooksAggregates(ids: List<BookId>): List<SavedBookAggregate>
}

interface AuthorBookProvider {
    context(_: RaiseContext)
    suspend fun getBooksForAuthors(authorIds: List<AuthorId>): Map<AuthorId, List<SavedBookRoot>>
}

interface SeriesBookProvider {
    context(_: RaiseContext)
    suspend fun getBooksForSeries(seriesIds: List<SeriesId>): Map<SeriesId, List<SavedBookRoot>>
}

interface LibraryBookProvider {
    context(_: RaiseContext)
    suspend fun getBooksForLibraries(
        libraryIds: List<LibraryId>
    ): Map<LibraryId, List<SavedBookRoot>>
}

interface BookModifier {
    context(_: RaiseContext)
    suspend fun createBook(title: String, path: StoragePath): SavedBookRoot

    context(_: RaiseContext)
    suspend fun updateBook(id: BookId, title: String?, path: StoragePath?): SavedBookRoot

    context(_: RaiseContext)
    suspend fun deleteBook(id: BookId): BookId

    context(_: RaiseContext)
    suspend fun linkAuthor(bookId: BookId, authorId: AuthorId)
}

interface BookAssetProvider {
    context(_: RaiseContext)
    suspend fun getPrimaryEdition(id: BookId): SavedEdition

    context(_: RaiseContext)
    suspend fun getEbookEdition(id: BookId): SavedEdition

    context(_: RaiseContext)
    suspend fun getPreferredCoverPath(id: BookId): StoragePath

    context(_: RaiseContext)
    suspend fun getThumbnailPath(id: BookId): StoragePath
}

interface BookService :
    BookProvider,
    AuthorBookProvider,
    SeriesBookProvider,
    LibraryBookProvider,
    BookModifier,
    BookAssetProvider {
    context(_: RaiseContext)
    suspend fun getBooksByAuthorPage(
        authorId: AuthorId,
        page: Int = 0,
        size: Int = 20,
        format: BookFormat? = null,
    ): BookPage

    context(_: RaiseContext)
    suspend fun getBooksBySeriesPage(
        seriesId: SeriesId,
        page: Int = 0,
        size: Int = 20,
        format: BookFormat? = null,
    ): BookPage
}

fun bookService(
    bookQueries: BookQueries,
    authorsProvider: BookAuthorProvider,
    seriesProvider: BookSeriesProvider,
    metadataProvider: MetadataProvider,
    authorQueries: io.tarantini.shelf.catalog.author.persistence.AuthorQueries,
    seriesQueries: io.tarantini.shelf.catalog.series.persistence.SeriesQueries,
    storageService: StorageService,
) =
    object : BookService {
        context(_: RaiseContext)
        private suspend fun resolveEdition(
            id: BookId,
            predicate: (SavedEdition) -> Boolean,
        ): SavedEdition =
            withContext(Dispatchers.IO) {
                ensureNotNull(
                    metadataProvider
                        .getMetadataForBook(id)
                        ?.editions
                        ?.map { it.edition }
                        ?.firstOrNull(predicate)
                ) {
                    EditionNotFound
                }
            }

        context(_: RaiseContext)
        override suspend fun getBooks() =
            withContext(Dispatchers.IO) {
                bookQueries.selectAll().executeAsList().map {
                    BookRoot.fromRaw(it.id, it.title, it.cover_path)
                }
            }

        context(_: RaiseContext)
        override suspend fun getBooksAggregate(): List<SavedBookAggregate> =
            withContext(Dispatchers.IO) {
                val books =
                    bookQueries.selectAll().executeAsList().map {
                        BookRoot.fromRaw(it.id, it.title, it.cover_path)
                    }
                val bookIds = books.map { it.id.id }
                val authorsMap = authorsProvider.getAuthorsForBooks(bookIds)
                val seriesMap = seriesProvider.getSeriesForBooks(bookIds)
                val metadataMap = metadataProvider.getMetadataForBooks(bookIds)
                books.map { book ->
                    BookAggregate(
                        book = book,
                        authors = authorsMap.getOrDefault(book.id.id, emptyList()),
                        series =
                            seriesMap.getOrDefault(book.id.id, emptyList()).map {
                                BookSeriesEntry(it.id.id, it.name, it.coverPath)
                            },
                        metadata = metadataMap[book.id.id],
                    )
                }
            }

        context(_: RaiseContext)
        override suspend fun getBooksPage(
            page: Int,
            size: Int,
            sortBy: String?,
            sortDir: String?,
            format: BookFormat?,
        ): BookPage =
            withContext(Dispatchers.IO) {
                val limit = size.toLong()
                val offset = (page * size).toLong()
                val actualSortBy = sortBy ?: "createdAt"
                val actualSortDir = sortDir ?: "DESC"

                val results =
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

                val pagedRoots =
                    results.executeAsList().map { BookRoot.fromRaw(it.id, it.title, it.cover_path) }

                val totalCount =
                    if (format != null) {
                        bookQueries.countBooksByFormat(format).executeAsOne()
                    } else {
                        bookQueries.countAll().executeAsOne()
                    }

                val pagedIds = pagedRoots.map { it.id.id }
                val pagedAuthorsMap = authorsProvider.getAuthorsForBooks(pagedIds)
                val pagedSeriesMap = seriesProvider.getSeriesForBooks(pagedIds)
                val pagedMetadataMap = metadataProvider.getMetadataForBooks(pagedIds)

                val items =
                    pagedRoots.map { book ->
                        BookAggregate(
                            book = book,
                            authors = pagedAuthorsMap.getOrDefault(book.id.id, emptyList()),
                            series =
                                pagedSeriesMap.getOrDefault(book.id.id, emptyList()).map {
                                    BookSeriesEntry(it.id.id, it.name, it.coverPath)
                                },
                            metadata = pagedMetadataMap[book.id.id],
                        )
                    }

                BookPage(items = items, totalCount = totalCount, page = page, size = size)
            }

        context(_: RaiseContext)
        override suspend fun getBooksByAuthorPage(
            authorId: AuthorId,
            page: Int,
            size: Int,
            format: BookFormat?,
        ): BookPage =
            withContext(Dispatchers.IO) {
                val limit = size.toLong()
                val offset = (page * size).toLong()

                val pagedRoots =
                    authorQueries
                        .selectBooksForAuthorPage(authorId, format, limit, offset)
                        .executeAsList()
                        .map { BookRoot.fromRaw(it.id, it.title, it.cover_path) }
                val totalCount = authorQueries.countBooksForAuthor(authorId, format).executeAsOne()

                val pagedIds = pagedRoots.map { it.id.id }
                val pagedAuthorsMap = authorsProvider.getAuthorsForBooks(pagedIds)
                val pagedSeriesMap = seriesProvider.getSeriesForBooks(pagedIds)
                val pagedMetadataMap = metadataProvider.getMetadataForBooks(pagedIds)

                val items =
                    pagedRoots.map { book ->
                        BookAggregate(
                            book = book,
                            authors = pagedAuthorsMap.getOrDefault(book.id.id, emptyList()),
                            series =
                                pagedSeriesMap.getOrDefault(book.id.id, emptyList()).map {
                                    BookSeriesEntry(it.id.id, it.name, it.coverPath)
                                },
                            metadata = pagedMetadataMap[book.id.id],
                        )
                    }
                BookPage(items = items, totalCount = totalCount, page = page, size = size)
            }

        context(_: RaiseContext)
        override suspend fun getBooksBySeriesPage(
            seriesId: SeriesId,
            page: Int,
            size: Int,
            format: BookFormat?,
        ): BookPage =
            withContext(Dispatchers.IO) {
                val limit = size.toLong()
                val offset = (page * size).toLong()

                val pagedRoots =
                    seriesQueries
                        .selectBooksForSeriesPage(seriesId, format, limit, offset)
                        .executeAsList()
                        .map { BookRoot.fromRaw(it.id, it.title, it.cover_path) }
                val totalCount = seriesQueries.countBooksForSeries(seriesId, format).executeAsOne()

                val pagedIds = pagedRoots.map { it.id.id }
                val pagedAuthorsMap = authorsProvider.getAuthorsForBooks(pagedIds)
                val pagedSeriesMap = seriesProvider.getSeriesForBooks(pagedIds)
                val pagedMetadataMap = metadataProvider.getMetadataForBooks(pagedIds)

                val items =
                    pagedRoots.map { book ->
                        BookAggregate(
                            book = book,
                            authors = pagedAuthorsMap.getOrDefault(book.id.id, emptyList()),
                            series =
                                pagedSeriesMap.getOrDefault(book.id.id, emptyList()).map {
                                    BookSeriesEntry(it.id.id, it.name, it.coverPath)
                                },
                            metadata = pagedMetadataMap[book.id.id],
                        )
                    }
                BookPage(items = items, totalCount = totalCount, page = page, size = size)
            }

        context(_: RaiseContext)
        override suspend fun getBookSummaries(): List<BookSummary> =
            withContext(Dispatchers.IO) {
                val books =
                    bookQueries.selectAll().executeAsList().map {
                        BookRoot.fromRaw(it.id, it.title, it.cover_path)
                    }
                val bookIds = books.map { it.id.id }
                val authorsMap = authorsProvider.getAuthorsForBooks(bookIds)
                val seriesMap = seriesProvider.getSeriesForBooks(bookIds)
                books.map { book ->
                    val seriesList = seriesMap[book.id.id] ?: emptyList()
                    val series = seriesList.firstOrNull()
                    BookSummary(
                        id = book.id.id,
                        title = book.title,
                        coverPath = book.coverPath,
                        authorNames = authorsMap[book.id.id]?.map { it.name } ?: emptyList(),
                        seriesName = series?.name,
                        seriesIndex = null,
                    )
                }
            }

        context(_: RaiseContext)
        override suspend fun getBook(id: BookId) =
            withContext(Dispatchers.IO) { bookQueries.getBookById(id) }

        context(_: RaiseContext)
        override suspend fun getBookAggregate(id: BookId) =
            withContext(Dispatchers.IO) {
                BookAggregate(
                    book = bookQueries.getBookById(id),
                    authors =
                        authorsProvider
                            .getAuthorsForBooks(listOf(id))
                            .getOrDefault(id, emptyList()),
                    series =
                        seriesProvider
                            .getBookSeriesEntries(listOf(id))
                            .getOrDefault(id, emptyList()),
                    metadata = metadataProvider.getMetadataForBook(id),
                )
            }

        context(_: RaiseContext)
        override suspend fun getPrimaryEdition(id: BookId): SavedEdition =
            resolveEdition(id) { true }

        context(_: RaiseContext)
        override suspend fun getEbookEdition(id: BookId): SavedEdition =
            resolveEdition(id) { it.format == BookFormat.EBOOK }

        context(_: RaiseContext)
        override suspend fun getPreferredCoverPath(id: BookId): StoragePath =
            withContext(Dispatchers.IO) {
                val coverPath = ensureNotNull(getBook(id).coverPath) { BookCoverNotFound }
                if (storageService.exists(coverPath.thumbnail())) coverPath.thumbnail()
                else coverPath
            }

        context(_: RaiseContext)
        override suspend fun getThumbnailPath(id: BookId): StoragePath =
            withContext(Dispatchers.IO) {
                val coverPath = ensureNotNull(getBook(id).coverPath) { BookCoverNotFound }
                if (storageService.exists(coverPath.thumbnail())) coverPath.thumbnail()
                else coverPath
            }

        context(_: RaiseContext)
        override suspend fun getBooksAggregates(ids: List<BookId>) =
            withContext(Dispatchers.IO) {
                if (ids.isEmpty()) return@withContext emptyList()

                val books =
                    bookQueries.selectByIds(ids).executeAsList().map {
                        BookRoot.fromRaw(it.id, it.title, it.cover_path)
                    }
                val rawIds = books.map { it.id.id }
                val authorsMap = authorsProvider.getAuthorsForBooks(rawIds)
                val seriesMap = seriesProvider.getSeriesForBooks(rawIds)
                val metadataMap = metadataProvider.getMetadataForBooks(rawIds)

                books.map { book ->
                    BookAggregate(
                        book = book,
                        authors = authorsMap.getOrDefault(book.id.id, emptyList()),
                        series =
                            seriesMap.getOrDefault(book.id.id, emptyList()).map {
                                BookSeriesEntry(it.id.id, it.name, it.coverPath)
                            },
                        metadata = metadataMap[book.id.id],
                    )
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

        context(_: RaiseContext)
        override suspend fun createBook(title: String, path: StoragePath): SavedBookRoot =
            withContext(Dispatchers.IO) {
                bookQueries.transactionWithResult {
                    val id = bookQueries.insert(title, path).executeAsOne()
                    bookQueries.getBookById(id)
                }
            }

        context(_: RaiseContext)
        override suspend fun updateBook(
            id: BookId,
            title: String?,
            path: StoragePath?,
        ): SavedBookRoot =
            withContext(Dispatchers.IO) {
                bookQueries.transactionWithResult {
                    val existing = bookQueries.getBookById(id)
                    bookQueries.update(title ?: existing.title, path ?: existing.coverPath, id)
                    bookQueries.getBookById(id)
                }
            }

        context(_: RaiseContext)
        override suspend fun deleteBook(id: BookId) =
            withContext(Dispatchers.IO) {
                bookQueries.transactionWithResult {
                    bookQueries.deleteBookAuthor(id)
                    bookQueries.deleteBookSeries(id)
                    bookQueries.deleteById(id).executeAsOne()
                }
            }

        context(_: RaiseContext)
        override suspend fun linkAuthor(bookId: BookId, authorId: AuthorId) {
            withContext(Dispatchers.IO) { bookQueries.insertBookAuthor(bookId, authorId) }
        }
    }
