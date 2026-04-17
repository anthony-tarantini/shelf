@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import.staging

import app.cash.sqldelight.Transacter
import arrow.core.raise.context.raise
import arrow.core.raise.recover
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.createAuthor
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.getAuthorById
import io.tarantini.shelf.catalog.author.linkBook
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.book.createBook
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookRoot
import io.tarantini.shelf.catalog.book.linkSeries
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.domain.*
import io.tarantini.shelf.catalog.metadata.domain.ASIN
import io.tarantini.shelf.catalog.metadata.persistence.MetadataQueries
import io.tarantini.shelf.catalog.metadata.saveAggregate
import io.tarantini.shelf.catalog.series.createSeries
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import io.tarantini.shelf.processing.import.domain.*
import io.tarantini.shelf.processing.storage.FileBytes
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.processing.storage.fetchRemoteImage
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.auth.requireOwnership
import io.tarantini.shelf.user.identity.domain.UserId
import java.time.Instant
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}
private val ALLOWED_COVER_HOSTS = listOf("hardcover.app")

private inline fun <T> tryIdentifier(
    rawValue: String?,
    warnings: MutableList<WarningDetail>,
    bookTitle: String,
    fieldName: String,
    construct: RaiseContext.(String) -> T,
): T? {
    if (rawValue == null) return null
    return recover({ construct(rawValue) }) { err ->
        warnings.add(
            WarningDetail(fileName = bookTitle, field = fieldName, message = err.toString())
        )
        null
    }
}

interface StagedBookService {
    context(_: RaiseContext, auth: JwtContext)
    suspend fun getAll(
        page: Int = 0,
        size: Int = 20,
        sortBy: String? = "createdAt",
        sortDir: String? = "DESC",
        author: String? = null,
    ): StagedBookPage

    context(_: RaiseContext, auth: JwtContext)
    suspend fun getById(id: String): StagedBook

    context(_: RaiseContext, auth: JwtContext)
    suspend fun update(
        stagedId: String,
        title: String? = null,
        description: String? = null,
        authors: List<String>? = null,
        selectedAuthorIds: Map<String, String?>? = null,
        publisher: String? = null,
        publishYear: Int? = null,
        genres: List<String>? = null,
        moods: List<String>? = null,
        series: List<StagedSeries>? = null,
        ebookMetadata: StagedEditionMetadata? = null,
        audiobookMetadata: StagedEditionMetadata? = null,
        coverUrl: String? = null,
    )

    context(_: RaiseContext, auth: JwtContext)
    suspend fun delete(stagedId: String)

    context(raise: RaiseContext, auth: JwtContext, transacter: Transacter)
    suspend fun promote(stagedId: String): List<WarningDetail>

    context(raise: RaiseContext, auth: JwtContext, transacter: Transacter)
    suspend fun merge(stagedId: String, targetBookId: String): List<WarningDetail>

    context(raise: RaiseContext, auth: JwtContext)
    suspend fun batch(request: StagedBatchRequest)

    context(_: RaiseContext, auth: JwtContext)
    suspend fun getBatchProgress(): BatchProgress?
}

fun stagedBookService(
    stagedBookStore: StagedBookStore,
    storageService: StorageService,
    bookQueries: BookQueries,
    authorQueries: AuthorQueries,
    seriesQueries: SeriesQueries,
    metadataQueries: MetadataQueries,
    scope: CoroutineScope,
    transacter: Transacter,
) =
    object : StagedBookService {

        private val batchProgress = mutableMapOf<UserId, BatchProgress>()
        private val batchProgressMutex = Mutex()

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun getAll(
            page: Int,
            size: Int,
            sortBy: String?,
            sortDir: String?,
            author: String?,
        ) =
            withContext(Dispatchers.IO) {
                val allForUser =
                    stagedBookStore
                        .getAll()
                        .values
                        .filter { it.userId == auth.userId }
                        .filter { book ->
                            author == null ||
                                book.authors.any { it.contains(author, ignoreCase = true) }
                        }
                val totalCount = allForUser.size.toLong()

                val sorted =
                    if (sortBy != null) {
                        val comparator =
                            when (sortBy) {
                                "title" -> compareBy<StagedBook> { it.title }
                                "author" -> compareBy { it.authors.firstOrNull() ?: "" }
                                "createdAt" -> compareBy { it.createdAt }
                                "mediaType" -> compareBy { it.mediaType }
                                else -> compareBy { it.createdAt }
                            }
                        if (sortDir == "DESC") allForUser.sortedWith(comparator.reversed())
                        else allForUser.sortedWith(comparator)
                    } else {
                        allForUser
                    }

                val items = sorted.drop(page * size).take(size)

                StagedBookPage(items = items, totalCount = totalCount, page = page, size = size)
            }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun getById(id: String) =
            withContext(Dispatchers.IO) {
                val stagedBook = stagedBookStore.get(id)
                requireOwnership(stagedBook.userId)
                stagedBook
            }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun update(
            stagedId: String,
            title: String?,
            description: String?,
            authors: List<String>?,
            selectedAuthorIds: Map<String, String?>?,
            publisher: String?,
            publishYear: Int?,
            genres: List<String>?,
            moods: List<String>?,
            series: List<StagedSeries>?,
            ebookMetadata: StagedEditionMetadata?,
            audiobookMetadata: StagedEditionMetadata?,
            coverUrl: String?,
        ) =
            withContext(Dispatchers.IO) {
                var stagedBook = stagedBookStore.get(stagedId)
                requireOwnership(stagedBook.userId)
                var updated = false

                if (!title.isNullOrBlank()) {
                    stagedBook = stagedBook.copy(title = title)
                    updated = true
                }
                if (description != null) {
                    stagedBook = stagedBook.copy(description = description)
                    updated = true
                }
                if (authors != null) {
                    stagedBook = stagedBook.copy(authors = authors)
                    updated = true
                }
                if (selectedAuthorIds != null) {
                    val mapped =
                        selectedAuthorIds.mapValues { (_, id) -> id?.let { AuthorId.fromRaw(it) } }
                    stagedBook = stagedBook.copy(selectedAuthorIds = mapped)
                    updated = true
                }
                if (publisher != null) {
                    stagedBook = stagedBook.copy(publisher = publisher)
                    updated = true
                }
                if (publishYear != null) {
                    stagedBook = stagedBook.copy(publishYear = publishYear)
                    updated = true
                }
                if (genres != null) {
                    stagedBook = stagedBook.copy(genres = genres)
                    updated = true
                }
                if (moods != null) {
                    stagedBook = stagedBook.copy(moods = moods)
                    updated = true
                }
                if (series != null) {
                    stagedBook = stagedBook.copy(series = series)
                    updated = true
                }
                if (ebookMetadata != null) {
                    stagedBook = stagedBook.copy(ebookMetadata = ebookMetadata)
                    updated = true
                }
                if (audiobookMetadata != null) {
                    stagedBook = stagedBook.copy(audiobookMetadata = audiobookMetadata)
                    updated = true
                }
                if (!coverUrl.isNullOrBlank()) {
                    val (bytes, extension) = fetchRemoteImage(coverUrl, ALLOWED_COVER_HOSTS)
                    val baseDir =
                        StoragePath.fromRaw(stagedBook.storagePath).parent()
                            ?: StoragePath.fromRaw("staged")
                    val coverStoragePath = baseDir.resolve("cover.$extension")

                    stagedBook.coverPath?.let { oldPath ->
                        val old = StoragePath.fromRaw(oldPath)
                        if (old.value != coverStoragePath.value) {
                            recover({ storageService.delete(old) }) {}
                            recover({ storageService.delete(old.thumbnail()) }) {}
                        }
                    }

                    storageService.save(coverStoragePath, FileBytes(bytes))
                    generateThumbnailIfPossible(coverStoragePath)
                    stagedBook = stagedBook.copy(coverPath = coverStoragePath.value)
                    updated = true
                }

                if (updated) {
                    stagedBookStore.put(stagedBook)
                }
            }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun delete(stagedId: String) =
            withContext(Dispatchers.IO) {
                val stagedBook = stagedBookStore.get(stagedId)
                requireOwnership(stagedBook.userId)

                recover({
                    val bookPath = StoragePath.fromRaw(stagedBook.storagePath)
                    storageService.delete(bookPath)
                    stagedBook.coverPath?.let {
                        val coverPath = StoragePath.fromRaw(it)
                        storageService.delete(coverPath)
                        // Also attempt to delete thumbnail
                        storageService.delete(coverPath.thumbnail())
                    }

                    // Attempt to clean up parent directories (Title and Author folders)
                    // These will fail safely (swallowed by recover) if the directories are not
                    // empty
                    bookPath.parent()?.let { titlePath ->
                        storageService.delete(titlePath)
                        titlePath.parent()?.let { authorPath -> storageService.delete(authorPath) }
                    }
                }) {
                    // Log error or continue so the UI still removes the item
                }

                stagedBookStore.remove(stagedId)
            }

        context(raise: RaiseContext, auth: JwtContext, transacter: Transacter)
        override suspend fun promote(stagedId: String): List<WarningDetail> =
            withContext(Dispatchers.IO) {
                val stagedBook = stagedBookStore.get(stagedId)
                requireOwnership(stagedBook.userId)

                val coverPath =
                    stagedBook.coverPath?.let { StoragePath.fromRaw(it) }
                        ?: BookRoot.deriveCoverPath(stagedBook.storagePath)

                generateThumbnailIfPossible(coverPath)

                var warnings = emptyList<WarningDetail>()
                transacter.transaction {
                    val book =
                        bookQueries.createBook(title = stagedBook.title, coverPath = coverPath)

                    warnings = with(raise) { promoteToBook(stagedBook, book) }

                    // 5. Remove from staging
                    stagedBookStore.remove(stagedId)
                }
                warnings
            }

        context(raise: RaiseContext, auth: JwtContext, transacter: Transacter)
        override suspend fun merge(stagedId: String, targetBookId: String): List<WarningDetail> =
            withContext(Dispatchers.IO) {
                val stagedBook = stagedBookStore.get(stagedId)
                requireOwnership(stagedBook.userId)

                val coverPath = stagedBook.coverPath?.let { StoragePath.fromRaw(it) }
                generateThumbnailIfPossible(coverPath)

                var warnings = emptyList<WarningDetail>()
                transacter.transaction {
                    val book = BookId(targetBookId)

                    warnings = with(raise) { promoteToBook(stagedBook, book) }

                    // 4. Remove from staging
                    stagedBookStore.remove(stagedId)
                }
                warnings
            }

        private suspend fun generateThumbnailIfPossible(coverPath: StoragePath?) {
            if (coverPath == null) return
            recover({
                if (storageService.exists(coverPath)) {
                    storageService.generateThumbnail(coverPath)
                }
            }) {
                logger.warn { "Failed to generate thumbnail for ${coverPath.value}: $it" }
            }
        }

        private fun RaiseContext.promoteToBook(
            stagedBook: StagedBook,
            book: BookId,
        ): List<WarningDetail> {
            val warnings = mutableListOf<WarningDetail>()

            // 1. Link Authors
            val authorIds =
                stagedBook.authors.map { authorName ->
                    val selectedId = stagedBook.selectedAuthorIds[authorName]
                    val author =
                        if (selectedId != null) {
                            authorQueries.getAuthorById(selectedId).id.id
                        } else {
                            authorQueries.createAuthor(authorName.trim())
                        }
                    authorQueries.linkBook(author, book)
                    author
                }

            // 2. Link Series
            stagedBook.series.forEach { stagedSeries ->
                val seriesId = seriesQueries.createSeries(stagedSeries.name)
                bookQueries.linkSeries(book, seriesId, stagedSeries.index ?: 0.0)
                authorIds.forEach { authorId ->
                    seriesQueries.insertSeriesAuthor(seriesId, authorId)
                }
            }

            // 3. Create/Update Metadata Aggregate
            val root =
                NewMetadataRoot(
                    id = Identity.Unsaved,
                    bookId = book,
                    title = stagedBook.title,
                    description = stagedBook.description,
                    publisher = stagedBook.publisher,
                    published = stagedBook.publishYear,
                    language = null,
                    genres = stagedBook.genres,
                    moods = stagedBook.moods,
                )

            val editionsToCreate = mutableListOf<EditionWithChapters<PersistenceState.Unsaved>>()

            // EBOOK Edition
            if (stagedBook.mediaType == MediaType.EBOOK || stagedBook.ebookMetadata != null) {
                val isPrimary = stagedBook.mediaType == MediaType.EBOOK
                val meta = stagedBook.ebookMetadata
                val edition =
                    NewEdition(
                        id = Identity.Unsaved,
                        bookId = book,
                        format = BookFormat.EBOOK,
                        fileHash = meta?.fileHash,
                        path =
                            meta?.storagePath?.let { StoragePath.fromRaw(it) }
                                ?: StoragePath.fromRaw(stagedBook.storagePath),
                        narrator = meta?.narrator,
                        isbn10 =
                            tryIdentifier(meta?.isbn10, warnings, stagedBook.title, "ISBN10") {
                                ISBN10(it)
                            },
                        isbn13 =
                            tryIdentifier(meta?.isbn13, warnings, stagedBook.title, "ISBN13") {
                                ISBN13(it)
                            },
                        asin =
                            tryIdentifier(meta?.asin, warnings, stagedBook.title, "ASIN") {
                                ASIN(it)
                            },
                        pages = meta?.pages?.toLong(),
                        totalTime = meta?.totalTime,
                        size = if (isPrimary) stagedBook.size else 0L,
                    )
                editionsToCreate.add(
                    EditionWithChapters(
                        edition = edition,
                        chapters = if (isPrimary) stagedBook.chapters else emptyList(),
                    )
                )
            }

            // AUDIOBOOK Edition
            if (
                stagedBook.mediaType == MediaType.AUDIOBOOK || stagedBook.audiobookMetadata != null
            ) {
                val isPrimary = stagedBook.mediaType == MediaType.AUDIOBOOK
                val meta = stagedBook.audiobookMetadata
                val edition =
                    NewEdition(
                        id = Identity.Unsaved,
                        bookId = book,
                        format = BookFormat.AUDIOBOOK,
                        fileHash = meta?.fileHash,
                        path =
                            meta?.storagePath?.let { StoragePath.fromRaw(it) }
                                ?: StoragePath.fromRaw(stagedBook.storagePath),
                        narrator = meta?.narrator,
                        isbn10 =
                            tryIdentifier(meta?.isbn10, warnings, stagedBook.title, "ISBN10") {
                                ISBN10(it)
                            },
                        isbn13 =
                            tryIdentifier(meta?.isbn13, warnings, stagedBook.title, "ISBN13") {
                                ISBN13(it)
                            },
                        asin =
                            tryIdentifier(meta?.asin, warnings, stagedBook.title, "ASIN") {
                                ASIN(it)
                            },
                        pages = meta?.pages?.toLong(),
                        totalTime = meta?.totalTime,
                        size = if (isPrimary) stagedBook.size else 0L,
                    )
                editionsToCreate.add(
                    EditionWithChapters(
                        edition = edition,
                        chapters = if (isPrimary) stagedBook.chapters else emptyList(),
                    )
                )
            }

            val aggregate = NewMetadataAggregate(metadata = root, editions = editionsToCreate)

            metadataQueries.saveAggregate(aggregate)

            if (warnings.isNotEmpty()) {
                logger.warn {
                    "Dropped invalid metadata for '${stagedBook.title}': ${warnings.joinToString { "${it.field}: ${it.message}" }}"
                }
            }

            return warnings
        }

        context(raise: RaiseContext, auth: JwtContext)
        override suspend fun batch(request: StagedBatchRequest) {
            val ids =
                withContext(Dispatchers.IO) {
                    if (request.action == StagedBatchAction.PROMOTE_ALL) {
                        getAll(page = 0, size = Int.MAX_VALUE, author = request.author).items.map {
                            it.id
                        }
                    } else {
                        request.ids
                    }
                }

            if (ids.isEmpty()) return

            val userId = auth.userId
            val runId = UUID.randomUUID().toString()

            batchProgressMutex.withLock {
                val current = batchProgress[userId]
                if (current != null && current.status == BatchStatus.RUNNING) {
                    raise(BatchAlreadyRunning)
                }
                batchProgress[userId] =
                    BatchProgress(
                        runId = runId,
                        status = BatchStatus.RUNNING,
                        action = request.action,
                        totalItems = ids.size,
                        completedItems = 0,
                        failedItems = 0,
                        startedAt = Instant.now().toString(),
                    )
            }

            // Capture auth context for the background coroutine
            val capturedAuth = auth

            scope.launch(Dispatchers.IO) {
                ids.forEach { id ->
                    recover({
                        val warnings =
                            with(capturedAuth) {
                                with(transacter) {
                                    when (request.action) {
                                        StagedBatchAction.PROMOTE,
                                        StagedBatchAction.PROMOTE_ALL -> promote(id)

                                        StagedBatchAction.DELETE -> {
                                            delete(id)
                                            emptyList()
                                        }
                                    }
                                }
                            }
                        incrementBatchCompleted(userId, runId)
                        if (warnings.isNotEmpty()) {
                            recordBatchWarnings(userId, runId, warnings)
                        }
                    }) { err ->
                        logger.error { "Failed to ${request.action} staged book $id: $err" }
                        recordBatchFailure(userId, runId, id, err.toString())
                    }
                }
                finishBatch(userId, runId)
            }
        }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun getBatchProgress(): BatchProgress? =
            batchProgressMutex.withLock { batchProgress[auth.userId] }

        private suspend fun incrementBatchCompleted(userId: UserId, runId: String) {
            batchProgressMutex.withLock {
                val current = batchProgress[userId] ?: return@withLock
                if (current.runId != runId) return@withLock
                batchProgress[userId] = current.copy(completedItems = current.completedItems + 1)
            }
        }

        private suspend fun recordBatchFailure(
            userId: UserId,
            runId: String,
            itemId: String,
            errorMessage: String,
        ) {
            batchProgressMutex.withLock {
                val current = batchProgress[userId] ?: return@withLock
                if (current.runId != runId) return@withLock
                val detail = FailedFileDetail(fileName = itemId, errorMessage = errorMessage)
                val details = (current.failedItemDetails + detail).takeLast(50)
                batchProgress[userId] =
                    current.copy(failedItems = current.failedItems + 1, failedItemDetails = details)
            }
        }

        private suspend fun recordBatchWarnings(
            userId: UserId,
            runId: String,
            warnings: List<WarningDetail>,
        ) {
            batchProgressMutex.withLock {
                val current = batchProgress[userId] ?: return@withLock
                if (current.runId != runId) return@withLock
                val details = (current.warningDetails + warnings).takeLast(100)
                batchProgress[userId] =
                    current.copy(warningItems = current.warningItems + 1, warningDetails = details)
            }
        }

        private suspend fun finishBatch(userId: UserId, runId: String) {
            batchProgressMutex.withLock {
                val current = batchProgress[userId] ?: return@withLock
                if (current.runId != runId) return@withLock
                batchProgress[userId] =
                    current.copy(
                        status = BatchStatus.COMPLETED,
                        finishedAt = Instant.now().toString(),
                    )
            }
        }
    }
