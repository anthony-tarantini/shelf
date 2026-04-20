@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import arrow.core.raise.context.ensureNotNull
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.BookAuthorProvider
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.book.domain.*
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.MetadataProvider
import io.tarantini.shelf.catalog.metadata.MetadataRepository
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.EditionNotFound
import io.tarantini.shelf.catalog.metadata.domain.SavedEdition
import io.tarantini.shelf.catalog.series.BookSeriesProvider
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import io.tarantini.shelf.organization.library.domain.LibraryId
import io.tarantini.shelf.organization.settings.SettingsService
import io.tarantini.shelf.processing.jobs.JobQueue
import io.tarantini.shelf.processing.storage.FileBytes
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.processing.storage.fetchRemoteImage
import io.tarantini.shelf.user.activity.domain.ReadStatus
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BookProvider {
    context(_: RaiseContext)
    suspend fun getBooks(): List<SavedBookRoot>

    context(_: RaiseContext)
    suspend fun getBooksAggregate(): List<SavedBookAggregate>

    context(_: RaiseContext)
    suspend fun getBookSummaries(): List<BookSummary>

    context(_: RaiseContext)
    suspend fun getBook(id: BookId): SavedBookRoot
}

interface BookAggregateProvider {
    context(_: RaiseContext)
    suspend fun getBookAggregate(id: BookId): SavedBookAggregate

    context(_: RaiseContext)
    suspend fun getBooksAggregates(ids: List<BookId>): List<SavedBookAggregate>
}

interface BookPagingProvider {
    context(_: RaiseContext)
    suspend fun getBooksPage(
        page: Int = 0,
        size: Int = 20,
        sortBy: String? = "createdAt",
        sortDir: String? = "DESC",
        titleQuery: String? = null,
        authorQuery: String? = null,
        seriesQuery: String? = null,
        status: ReadStatus? = null,
        format: BookFormat? = null,
        userId: UserId? = null,
    ): BookPage

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

interface AuthorBookProvider {
    context(_: RaiseContext)
    suspend fun getBooksForAuthors(authorIds: List<AuthorId>): Map<AuthorId, List<SavedBookRoot>>
}

interface SeriesBookProvider {
    context(_: RaiseContext)
    suspend fun getBooksForSeries(seriesIds: List<SeriesId>): Map<SeriesId, List<SavedBookRoot>>
}

interface AuthorBookSummaryProvider {
    context(_: RaiseContext)
    suspend fun getBookSummariesForAuthor(
        authorId: AuthorId,
        page: Int = 0,
        size: Int = Int.MAX_VALUE,
        format: BookFormat? = null,
    ): List<BookSummary>
}

interface SeriesBookSummaryProvider {
    context(_: RaiseContext)
    suspend fun getBookSummariesForSeries(seriesId: SeriesId): List<BookSummary>
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

interface BookMetadataModifier {
    context(_: RaiseContext)
    suspend fun updateBookMetadata(userId: UserId, id: BookId, command: UpdateBookMetadataCommand)
}

interface BookService :
    BookProvider,
    BookAggregateProvider,
    BookPagingProvider,
    AuthorBookProvider,
    SeriesBookProvider,
    AuthorBookSummaryProvider,
    SeriesBookSummaryProvider,
    LibraryBookProvider,
    BookModifier,
    BookAssetProvider,
    BookMetadataModifier

private val ALLOWED_COVER_HOSTS = listOf("hardcover.app")

fun bookService(
    bookQueries: BookQueries,
    authorsProvider: BookAuthorProvider,
    seriesProvider: BookSeriesProvider,
    metadataProvider: MetadataProvider,
    authorQueries: AuthorQueries,
    seriesQueries: SeriesQueries,
    storageService: StorageService,
    metadataRepository: MetadataRepository,
    settingsService: SettingsService,
    jobQueue: JobQueue,
): BookService {
    val readRepository =
        bookReadRepository(
            bookQueries = bookQueries,
            authorQueries = authorQueries,
            seriesQueries = seriesQueries,
        )
    val readService =
        BookReadService(
            repository = readRepository,
            authorsProvider = authorsProvider,
            seriesProvider = seriesProvider,
            metadataProvider = metadataProvider,
        )
    val writeService =
        BookWriteService(
            bookQueries = bookQueries,
            authorQueries = authorQueries,
            seriesQueries = seriesQueries,
        )
    val assetService =
        BookAssetService(
            bookProvider = readService,
            metadataProvider = metadataProvider,
            storageService = storageService,
        )
    val metadataUpdateService =
        BookMetadataUpdateService(
            repository =
                bookRepository(
                    bookQueries = bookQueries,
                    authorQueries = authorQueries,
                    seriesQueries = seriesQueries,
                    metadataRepository = metadataRepository,
                ),
            decider = DefaultBookMetadataDecider,
            storageService = storageService,
            settingsService = settingsService,
            eventHandler = JobQueueBookDomainEventHandler(jobQueue),
        )

    return object :
        BookService,
        BookProvider by readService,
        BookAggregateProvider by readService,
        BookPagingProvider by readService,
        AuthorBookProvider by readService,
        SeriesBookProvider by readService,
        AuthorBookSummaryProvider by readService,
        SeriesBookSummaryProvider by readService,
        LibraryBookProvider by readService,
        BookModifier by writeService,
        BookAssetProvider by assetService,
        BookMetadataModifier by metadataUpdateService {}
}

private class BookReadService(
    private val repository: BookReadRepository,
    private val authorsProvider: BookAuthorProvider,
    private val seriesProvider: BookSeriesProvider,
    private val metadataProvider: MetadataProvider,
) :
    BookProvider,
    BookAggregateProvider,
    BookPagingProvider,
    AuthorBookProvider,
    SeriesBookProvider,
    AuthorBookSummaryProvider,
    SeriesBookSummaryProvider,
    LibraryBookProvider {
    context(_: RaiseContext)
    override suspend fun getBooks(): List<SavedBookRoot> = repository.getAllBooks()

    context(_: RaiseContext)
    override suspend fun getBooksAggregate(): List<SavedBookAggregate> =
        withContext(Dispatchers.IO) {
            val books = repository.getAllBooks()
            buildAggregates(books)
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
    ): BookPage =
        withContext(Dispatchers.IO) {
            val pagedRoots =
                repository.getBooksPage(
                    userId = userId,
                    page = page,
                    size = size,
                    sortBy = sortBy,
                    sortDir = sortDir,
                    titleQuery = titleQuery,
                    authorQuery = authorQuery,
                    seriesQuery = seriesQuery,
                    status = status,
                    format = format,
                )
            val totalCount =
                repository.countBooks(
                    userId = userId,
                    titleQuery = titleQuery,
                    authorQuery = authorQuery,
                    seriesQuery = seriesQuery,
                    status = status,
                    format = format,
                )

            BookPage(
                items = buildAggregates(pagedRoots),
                totalCount = totalCount,
                page = page,
                size = size,
            )
        }

    context(_: RaiseContext)
    override suspend fun getBooksByAuthorPage(
        authorId: AuthorId,
        page: Int,
        size: Int,
        format: BookFormat?,
    ): BookPage =
        withContext(Dispatchers.IO) {
            val pagedRoots = repository.getBooksByAuthorPage(authorId, page, size, format)
            val totalCount = repository.countBooksForAuthor(authorId, format)

            BookPage(
                items = buildAggregates(pagedRoots),
                totalCount = totalCount,
                page = page,
                size = size,
            )
        }

    context(_: RaiseContext)
    override suspend fun getBooksBySeriesPage(
        seriesId: SeriesId,
        page: Int,
        size: Int,
        format: BookFormat?,
    ): BookPage =
        withContext(Dispatchers.IO) {
            val pagedRoots = repository.getBooksBySeriesPage(seriesId, page, size, format)
            val totalCount = repository.countBooksForSeries(seriesId, format)

            BookPage(
                items = buildAggregates(pagedRoots),
                totalCount = totalCount,
                page = page,
                size = size,
            )
        }

    context(_: RaiseContext)
    override suspend fun getBookSummaries(): List<BookSummary> =
        withContext(Dispatchers.IO) {
            val books = repository.getAllBooks()
            val bookIds = books.map { it.id.id }
            val authorsMap = authorsProvider.getAuthorsForBooks(bookIds)
            val seriesMap = seriesProvider.getBookSeriesEntries(bookIds)
            books.map { book ->
                val seriesList = seriesMap[book.id.id] ?: emptyList()
                val series = seriesList.firstOrNull()
                BookSummary(
                    id = book.id.id,
                    title = book.title,
                    coverPath = book.coverPath,
                    authorNames = authorsMap[book.id.id]?.map { it.name } ?: emptyList(),
                    seriesName = series?.name,
                    seriesIndex = series?.index,
                )
            }
        }

    context(_: RaiseContext)
    override suspend fun getBook(id: BookId): SavedBookRoot = repository.getBook(id)

    context(_: RaiseContext)
    override suspend fun getBookAggregate(id: BookId): SavedBookAggregate =
        withContext(Dispatchers.IO) {
            BookAggregate(
                book = repository.getBook(id),
                authors =
                    authorsProvider.getAuthorsForBooks(listOf(id)).getOrDefault(id, emptyList()),
                series =
                    seriesProvider.getBookSeriesEntries(listOf(id)).getOrDefault(id, emptyList()),
                metadata = metadataProvider.getMetadataForBook(id),
            )
        }

    context(_: RaiseContext)
    override suspend fun getBooksAggregates(ids: List<BookId>): List<SavedBookAggregate> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyList()
            val books = repository.getBooksByIds(ids)
            buildAggregates(books)
        }

    context(_: RaiseContext)
    override suspend fun getBooksForAuthors(
        authorIds: List<AuthorId>
    ): Map<AuthorId, List<SavedBookRoot>> = repository.getBooksForAuthors(authorIds)

    context(_: RaiseContext)
    override suspend fun getBooksForSeries(
        seriesIds: List<SeriesId>
    ): Map<SeriesId, List<SavedBookRoot>> = repository.getBooksForSeries(seriesIds)

    context(_: RaiseContext)
    override suspend fun getBookSummariesForAuthor(
        authorId: AuthorId,
        page: Int,
        size: Int,
        format: BookFormat?,
    ): List<BookSummary> =
        withContext(Dispatchers.IO) {
            getBooksByAuthorPage(authorId = authorId, page = page, size = size, format = format)
                .items
                .map { it.toSummary() }
        }

    context(_: RaiseContext)
    override suspend fun getBookSummariesForSeries(seriesId: SeriesId): List<BookSummary> =
        withContext(Dispatchers.IO) {
            repository.getBooksForSeries(listOf(seriesId)).getOrDefault(seriesId, emptyList()).map {
                it.toSummary()
            }
        }

    context(_: RaiseContext)
    override suspend fun getBooksForLibraries(
        libraryIds: List<LibraryId>
    ): Map<LibraryId, List<SavedBookRoot>> = repository.getBooksForLibraries(libraryIds)

    context(_: RaiseContext)
    private suspend fun buildAggregates(books: List<SavedBookRoot>): List<SavedBookAggregate> {
        if (books.isEmpty()) return emptyList()

        val bookIds = books.map { it.id.id }
        val authorsMap = authorsProvider.getAuthorsForBooks(bookIds)
        val seriesMap = seriesProvider.getBookSeriesEntries(bookIds)
        val metadataMap = metadataProvider.getMetadataForBooks(bookIds)
        return books.map { book ->
            BookAggregate(
                book = book,
                authors = authorsMap.getOrDefault(book.id.id, emptyList()),
                series = seriesMap.getOrDefault(book.id.id, emptyList()),
                metadata = metadataMap[book.id.id],
            )
        }
    }

    private fun SavedBookAggregate.toSummary(): BookSummary {
        val series = series.firstOrNull()
        return BookSummary(
            id = book.id.id,
            title = book.title,
            coverPath = book.coverPath,
            authorNames = authors.map { it.name },
            seriesName = series?.name,
            seriesIndex = series?.index,
        )
    }

    private fun SavedBookRoot.toSummary(): BookSummary =
        BookSummary(id = id.id, title = title, coverPath = coverPath)
}

private class BookAssetService(
    private val bookProvider: BookProvider,
    private val metadataProvider: MetadataProvider,
    private val storageService: StorageService,
) : BookAssetProvider {
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
    override suspend fun getPrimaryEdition(id: BookId): SavedEdition = resolveEdition(id) { true }

    context(_: RaiseContext)
    override suspend fun getEbookEdition(id: BookId): SavedEdition =
        resolveEdition(id) { it.format == BookFormat.EBOOK }

    context(_: RaiseContext)
    override suspend fun getPreferredCoverPath(id: BookId): StoragePath =
        withContext(Dispatchers.IO) {
            val coverPath = ensureNotNull(bookProvider.getBook(id).coverPath) { BookCoverNotFound }
            if (storageService.exists(coverPath.thumbnail())) coverPath.thumbnail() else coverPath
        }

    context(_: RaiseContext)
    override suspend fun getThumbnailPath(id: BookId): StoragePath =
        withContext(Dispatchers.IO) {
            val coverPath = ensureNotNull(bookProvider.getBook(id).coverPath) { BookCoverNotFound }
            if (storageService.exists(coverPath.thumbnail())) coverPath.thumbnail() else coverPath
        }
}

private class BookWriteService(
    private val bookQueries: BookQueries,
    private val authorQueries: io.tarantini.shelf.catalog.author.persistence.AuthorQueries,
    private val seriesQueries: io.tarantini.shelf.catalog.series.persistence.SeriesQueries,
) : BookModifier {
    context(_: RaiseContext)
    override suspend fun createBook(title: String, path: StoragePath): SavedBookRoot =
        withContext(Dispatchers.IO) {
            bookQueries.transactionWithResult {
                val id = bookQueries.insert(title, path).executeAsOne()
                bookQueries.getBookById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun updateBook(id: BookId, title: String?, path: StoragePath?): SavedBookRoot =
        withContext(Dispatchers.IO) {
            bookQueries.transactionWithResult {
                val existing = bookQueries.getBookById(id)
                bookQueries
                    .update(title ?: existing.title, path ?: existing.coverPath, id)
                    .executeAsOne()
                bookQueries.getBookById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun deleteBook(id: BookId): BookId =
        withContext(Dispatchers.IO) {
            bookQueries.transactionWithResult {
                bookQueries.deleteBookAuthor(id)
                bookQueries.deleteBookSeries(id)
                val deletedId = bookQueries.deleteById(id).executeAsOne()
                authorQueries.deleteOrphanedAuthors()
                seriesQueries.deleteOrphanedSeries()
                deletedId
            }
        }

    context(_: RaiseContext)
    override suspend fun linkAuthor(bookId: BookId, authorId: AuthorId) {
        withContext(Dispatchers.IO) { bookQueries.insertBookAuthor(bookId, authorId) }
    }
}

internal class BookMetadataUpdateService(
    private val repository: BookRepository,
    private val decider: BookMetadataDecider,
    private val storageService: StorageService,
    private val settingsService: SettingsService,
    private val eventHandler: BookDomainEventHandler,
) : BookMetadataModifier {
    context(_: RaiseContext)
    override suspend fun updateBookMetadata(
        userId: UserId,
        id: BookId,
        command: UpdateBookMetadataCommand,
    ) {
        val snapshot = repository.loadMetadataSnapshot(id)

        var newCoverPath: StoragePath? = null
        if (command.coverUrl != null) {
            val (bytes, extension) = fetchRemoteImage(command.coverUrl.value, ALLOWED_COVER_HOSTS)
            val coverStoragePath = StoragePath.fromRaw("books/${id.value}/cover.$extension")
            storageService.save(coverStoragePath, FileBytes(bytes))
            newCoverPath = coverStoragePath
        }

        val settings = settingsService.getUserSettings(userId)
        val decision =
            decider.decide(
                snapshot = snapshot,
                command = command,
                resolvedCoverPath = newCoverPath,
                syncMetadataToFiles = settings.syncMetadataToFiles,
            )

        repository.applyMetadataMutation(id, decision.mutation)
        decision.events.forEach { event -> eventHandler.handle(event) }
    }
}

private class JobQueueBookDomainEventHandler(private val jobQueue: JobQueue) :
    BookDomainEventHandler {
    override suspend fun handle(event: BookDomainEvent) {
        when (event) {
            is BookDomainEvent.MetadataSyncRequested ->
                jobQueue.enqueueSyncMetadataJob(event.bookId)
        }
    }
}
