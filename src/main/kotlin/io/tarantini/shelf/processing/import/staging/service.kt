@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import.staging

import app.cash.sqldelight.Transacter
import arrow.core.raise.context.raise
import arrow.core.raise.recover
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.author.createAuthor
import io.tarantini.shelf.catalog.author.getAuthorById
import io.tarantini.shelf.catalog.author.linkBook
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.book.createBook
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookRoot
import io.tarantini.shelf.catalog.book.linkSeries
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.MetadataRepository
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
    suspend fun getPreferredCoverPath(stagedId: String): StoragePath

    context(_: RaiseContext, auth: JwtContext)
    suspend fun update(stagedId: String, command: UpdateStagedBookCommand)

    context(_: RaiseContext, auth: JwtContext)
    suspend fun delete(stagedId: String)

    context(raise: RaiseContext, auth: JwtContext, transacter: Transacter)
    suspend fun promote(command: PromoteStagedBookCommand): List<WarningDetail>

    context(raise: RaiseContext, auth: JwtContext, transacter: Transacter)
    suspend fun merge(stagedId: String, command: MergeStagedBookCommand): List<WarningDetail>

    context(raise: RaiseContext, auth: JwtContext)
    suspend fun batch(command: StagedBatchCommand)

    context(_: RaiseContext, auth: JwtContext)
    suspend fun getBatchProgress(): BatchProgress?
}

fun stagedBookService(
    stagedBookStore: StagedBookStore,
    storageService: StorageService,
    bookQueries: BookQueries,
    authorQueries: AuthorQueries,
    seriesQueries: SeriesQueries,
    metadataRepository: MetadataRepository,
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
        override suspend fun getPreferredCoverPath(stagedId: String): StoragePath =
            withContext(Dispatchers.IO) {
                val stagedBook = stagedBookStore.get(stagedId)
                requireOwnership(stagedBook.userId)
                val coverPath =
                    stagedBook.coverPath?.let { StoragePath(it) } ?: raise(StagedCoverNotFound)
                if (storageService.exists(coverPath.thumbnail())) coverPath.thumbnail()
                else coverPath
            }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun update(stagedId: String, command: UpdateStagedBookCommand) =
            withContext(Dispatchers.IO) {
                var stagedBook = stagedBookStore.get(stagedId)
                requireOwnership(stagedBook.userId)

                // Pure domain update
                var updated = StagedBookDecider.applyUpdate(stagedBook, command)

                // Cover URL requires I/O — handle separately
                val coverUrl = command.coverUrl
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
                    updated = updated.copy(coverPath = coverStoragePath.value)
                }

                if (updated != stagedBook) {
                    stagedBookStore.put(updated)
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
        override suspend fun promote(command: PromoteStagedBookCommand): List<WarningDetail> =
            withContext(Dispatchers.IO) {
                val stagedId = command.stagedId
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
        override suspend fun merge(
            stagedId: String,
            command: MergeStagedBookCommand,
        ): List<WarningDetail> =
            withContext(Dispatchers.IO) {
                val stagedBook = stagedBookStore.get(stagedId)
                requireOwnership(stagedBook.userId)

                val coverPath = stagedBook.coverPath?.let { StoragePath.fromRaw(it) }
                generateThumbnailIfPossible(coverPath)

                var warnings = emptyList<WarningDetail>()
                transacter.transaction {
                    val book = command.targetBookId

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

            // 3. Build + persist metadata aggregate (pure decision)
            val plan = StagedBookDecider.planPromotion(stagedBook, book)
            metadataRepository.saveAggregate(plan.metadata)

            if (plan.warnings.isNotEmpty()) {
                logger.warn {
                    "Dropped invalid metadata for '${stagedBook.title}': ${plan.warnings.joinToString { "${it.field}: ${it.message}" }}"
                }
            }

            return plan.warnings
        }

        context(raise: RaiseContext, auth: JwtContext)
        override suspend fun batch(command: StagedBatchCommand) {
            val ids =
                withContext(Dispatchers.IO) {
                    if (command.action == StagedBatchAction.PROMOTE_ALL) {
                        getAll(page = 0, size = Int.MAX_VALUE, author = command.author).items.map {
                            it.id
                        }
                    } else {
                        command.ids
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
                        action = command.action,
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
                                    when (command.action) {
                                        StagedBatchAction.PROMOTE,
                                        StagedBatchAction.PROMOTE_ALL ->
                                            promote(PromoteStagedBookCommand(id))

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
                        logger.error {
                            "Failed to ${command.action} staged book: ${err::class.simpleName}"
                        }
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
