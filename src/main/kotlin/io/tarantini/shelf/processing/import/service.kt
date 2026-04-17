@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import

import arrow.core.raise.catch
import arrow.core.raise.context.raise
import arrow.core.raise.either
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.resourceScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.toHttpResponse
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.MetadataProcessor
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.MediaType
import io.tarantini.shelf.catalog.metadata.domain.ProcessedMetadata
import io.tarantini.shelf.integration.koreader.koreaderHash
import io.tarantini.shelf.observability.Observability
import io.tarantini.shelf.processing.import.domain.DirectoryNotFound
import io.tarantini.shelf.processing.import.domain.FailedFileDetail
import io.tarantini.shelf.processing.import.domain.ImportJob
import io.tarantini.shelf.processing.import.domain.ImportScanProgress
import io.tarantini.shelf.processing.import.domain.ImportScanStatus
import io.tarantini.shelf.processing.import.domain.StagedBook
import io.tarantini.shelf.processing.import.domain.StagedEditionMetadata
import io.tarantini.shelf.processing.import.domain.StagedSeries
import io.tarantini.shelf.processing.import.staging.StagedBookStore
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.identity.domain.UserId
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlin.String
import kotlin.io.path.deleteIfExists
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

interface ImportService {
    context(_: RaiseContext, auth: JwtContext)
    suspend fun importToStaging(sourcePath: Path, fileName: String): StagedBook

    context(_: RaiseContext, auth: JwtContext)
    suspend fun queueImport(sourcePath: Path, fileName: String, deleteSource: Boolean = false)

    context(_: RaiseContext, auth: JwtContext)
    suspend fun scanDirectory(path: String)

    context(_: RaiseContext, auth: JwtContext)
    suspend fun getScanProgress(): ImportScanProgress?
}

fun importService(
    storageService: StorageService,
    metadataProcessor: MetadataProcessor,
    stagedBookStore: StagedBookStore,
    allowedScanRoots: List<String>,
    scope: CoroutineScope,
    observability: Observability,
) =
    object : ImportService {
        private val jobChannel = Channel<ImportJob>(capacity = 100)
        private val normalizedScanRoots =
            allowedScanRoots.map { Paths.get(it).toAbsolutePath().normalize() }
        private val scanProgress = mutableMapOf<UserId, ImportScanProgress>()
        private val scanProgressMutex = Mutex()

        init {
            repeat(3) { id ->
                scope.launch(Dispatchers.IO) {
                    logger.info { "Import Worker-$id started" }
                    for (job in jobChannel) {
                        processImportJob(job, id)
                    }
                }
            }
        }

        private suspend fun processImportJob(job: ImportJob, workerId: Int) {
            resourceScope {
                val sample = io.micrometer.core.instrument.Timer.start(observability.meterRegistry)
                try {
                    if (job.deleteSource) {
                        install({ job.sourcePath }) { path, _ ->
                            catch({
                                path.deleteIfExists()
                                logger.debug { "Worker-$workerId: Deleted source file: $path" }
                            }) { e ->
                                logger.error(e) {
                                    "Worker-$workerId: Failed to delete source file: $path"
                                }
                            }
                        }
                    }

                    logger.info {
                        "Worker-$workerId: Processing background import for user ${job.userId} - ${job.fileName}"
                    }

                    catch({
                        either {
                                importToStagingInternal(
                                    this@resourceScope,
                                    job.userId,
                                    job.sourcePath,
                                    job.fileName,
                                )
                            }
                            .fold(
                                ifLeft = { error ->
                                    val msg =
                                        "${error::class.simpleName} - ${error.toHttpResponse().second}"
                                    recordScanFailure(job, msg)
                                    observability
                                        .counter("shelf.import.jobs", "result", "failure")
                                        .increment()
                                    logger.error {
                                        "Worker-$workerId: Background import failed for ${job.fileName}: $msg"
                                    }
                                },
                                ifRight = {
                                    recordScanSuccess(job)
                                    observability
                                        .counter("shelf.import.jobs", "result", "success")
                                        .increment()
                                },
                            )
                    }) { e ->
                        recordScanFailure(job, e.message ?: "Unexpected error")
                        observability.counter("shelf.import.jobs", "result", "failure").increment()
                        logger.error(e) {
                            "Worker-$workerId: Unexpected error during background import of ${job.fileName}"
                        }
                    }
                } finally {
                    sample.stop(observability.timer("shelf.import.job.duration"))
                }
            }
        }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun queueImport(
            sourcePath: Path,
            fileName: String,
            deleteSource: Boolean,
        ) {
            jobChannel.send(
                ImportJob(
                    auth.userId,
                    sourcePath,
                    fileName,
                    staged = true,
                    deleteSource = deleteSource,
                )
            )
        }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun scanDirectory(path: String) {
            withContext(Dispatchers.IO) {
                val rootPath = Paths.get(path).toAbsolutePath().normalize()
                if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
                    raise(DirectoryNotFound)
                }
                if (normalizedScanRoots.none { rootPath.startsWith(it) }) {
                    raise(DirectoryNotFound)
                }

                val supportedExtensions = listOf("epub", "mp3", "m4b", "m4a")
                logger.info { "Starting scan of directory: $path" }
                observability.counter("shelf.import.scans", "result", "started").increment()

                scope.launch(Dispatchers.IO) {
                    catch({
                        Files.walk(rootPath).use { stream ->
                            val files =
                                stream
                                    .filter { p ->
                                        Files.isRegularFile(p) &&
                                            supportedExtensions.any {
                                                p.toString().endsWith(".$it", ignoreCase = true)
                                            }
                                    }
                                    .toList()

                            logger.info { "Found ${files.size} supported files in $path" }
                            val runId = UUID.randomUUID().toString()
                            startScan(auth.userId, runId, rootPath, files.size)

                            files.forEach { file ->
                                val fileName = file.fileName.toString()
                                queueImportForScan(file, fileName, auth.userId, runId)
                                logger.info { "Queued import for file: ${file.toAbsolutePath()}" }
                            }

                            if (files.isEmpty()) {
                                completeZeroFileScan(auth.userId, runId)
                            }
                        }
                        logger.info { "Completed scan of directory: $path" }
                    }) { e ->
                        failActiveScan(auth.userId)
                        observability.counter("shelf.import.scans", "result", "failure").increment()
                        logger.error(e) { "Error walking directory: $path" }
                    }
                }
            }
        }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun getScanProgress(): ImportScanProgress? =
            scanProgressMutex.withLock { scanProgress[auth.userId] }

        private suspend fun queueImportForScan(
            sourcePath: Path,
            fileName: String,
            userId: UserId,
            runId: String,
        ) {
            jobChannel.send(
                ImportJob(
                    userId = userId,
                    sourcePath = sourcePath,
                    fileName = fileName,
                    staged = true,
                    deleteSource = false,
                    scanRunId = runId,
                )
            )
            markQueued(userId, runId)
        }

        private suspend fun startScan(
            userId: UserId,
            runId: String,
            rootPath: Path,
            totalFiles: Int,
        ) {
            val snapshot =
                ImportScanProgress(
                    runId = runId,
                    status = ImportScanStatus.RUNNING,
                    sourcePath = rootPath.toString(),
                    totalFiles = totalFiles,
                    queuedFiles = 0,
                    completedFiles = 0,
                    failedFiles = 0,
                    startedAt = Instant.now().toString(),
                )
            scanProgressMutex.withLock { scanProgress[userId] = snapshot }
        }

        private suspend fun markQueued(userId: UserId, runId: String) {
            scanProgressMutex.withLock {
                val current = scanProgress[userId] ?: return@withLock
                if (current.runId != runId) return@withLock
                scanProgress[userId] = current.copy(queuedFiles = current.queuedFiles + 1)
            }
        }

        private suspend fun recordScanSuccess(job: ImportJob) {
            val runId = job.scanRunId ?: return
            finishProcessedJob(job.userId, runId, succeeded = true)
        }

        private suspend fun recordScanFailure(job: ImportJob, errorMessage: String) {
            val runId = job.scanRunId ?: return
            val detail = FailedFileDetail(fileName = job.fileName, errorMessage = errorMessage)
            finishProcessedJob(job.userId, runId, succeeded = false, failedDetail = detail)
        }

        private suspend fun finishProcessedJob(
            userId: UserId,
            runId: String,
            succeeded: Boolean,
            failedDetail: FailedFileDetail? = null,
        ) {
            scanProgressMutex.withLock {
                val current = scanProgress[userId] ?: return@withLock
                if (current.runId != runId) return@withLock

                val updated =
                    if (succeeded) {
                        current.copy(completedFiles = current.completedFiles + 1)
                    } else {
                        val details =
                            if (failedDetail != null) {
                                (current.failedFileDetails + failedDetail).takeLast(50)
                            } else {
                                current.failedFileDetails
                            }
                        current.copy(
                            failedFiles = current.failedFiles + 1,
                            failedFileDetails = details,
                        )
                    }

                val processedCount = updated.completedFiles + updated.failedFiles
                scanProgress[userId] =
                    if (processedCount >= updated.totalFiles) {
                        observability
                            .counter("shelf.import.scans", "result", "completed")
                            .increment()
                        updated.copy(
                            status = ImportScanStatus.COMPLETED,
                            finishedAt = Instant.now().toString(),
                        )
                    } else {
                        updated
                    }
            }
        }

        private suspend fun completeZeroFileScan(userId: UserId, runId: String) {
            scanProgressMutex.withLock {
                val current = scanProgress[userId] ?: return@withLock
                if (current.runId != runId) return@withLock
                scanProgress[userId] =
                    current.copy(
                        status = ImportScanStatus.COMPLETED,
                        finishedAt = Instant.now().toString(),
                    )
                observability.counter("shelf.import.scans", "result", "completed").increment()
            }
        }

        private suspend fun failActiveScan(userId: UserId) {
            scanProgressMutex.withLock {
                val current = scanProgress[userId] ?: return@withLock
                scanProgress[userId] =
                    current.copy(
                        status = ImportScanStatus.FAILED,
                        finishedAt = Instant.now().toString(),
                    )
                observability.counter("shelf.import.scans", "result", "failure").increment()
            }
        }

        context(_: RaiseContext)
        private suspend fun prepareImport(
            scope: ResourceScope,
            sourcePath: Path,
            fileName: String,
        ): Triple<ProcessedMetadata, StoragePath, StoragePath?> {
            logger.debug { "Processing metadata for $fileName" }
            val tempBookId = BookId.fromRaw(UUID.randomUUID().toKotlinUuid())
            val processed = metadataProcessor.process(scope, sourcePath, fileName, tempBookId)
            logger.debug { "Metadata processed successfully for ${processed.metadata.title}" }

            val fileHash = withContext(Dispatchers.IO) { sourcePath.koreaderHash() }
            val processedWithHash =
                processed.copy(edition = processed.edition.copy(fileHash = fileHash))

            val authorName = StoragePath.safeSegment(processedWithHash.authors.firstOrNull())
            val bookName = StoragePath.safeSegment(processedWithHash.metadata.title)

            val baseDir = StoragePath.fromRaw("books").resolve(authorName).resolve(bookName)

            // 1. Save Book File
            val fileExtension = StoragePath.safeSegment(fileName.substringAfterLast("."), "bin")
            val bookStoragePath = baseDir.resolve("$bookName.$fileExtension")
            logger.info { "Saving book file to: $bookStoragePath" }
            storageService.save(bookStoragePath, sourcePath)
            logger.debug { "Book file saved successfully" }

            // 2. Save Cover if present
            var coverStoragePath: StoragePath? = null
            if (processedWithHash.coverImage != null) {
                val coverPath = baseDir.resolve("cover.jpg")
                logger.info { "Saving cover image to: $coverPath" }
                storageService.save(coverPath, processedWithHash.coverImage)
                coverStoragePath = coverPath
            }

            return Triple(processedWithHash, bookStoragePath, coverStoragePath)
        }

        context(_: RaiseContext, auth: JwtContext)
        override suspend fun importToStaging(sourcePath: Path, fileName: String) =
            withContext(Dispatchers.IO) {
                resourceScope { importToStagingInternal(this, auth.userId, sourcePath, fileName) }
            }

        context(_: RaiseContext)
        private suspend fun importToStagingInternal(
            scope: ResourceScope,
            userId: UserId,
            sourcePath: Path,
            fileName: String,
        ): StagedBook {
            logger.info { "Starting import to staging for file: $fileName, user: $userId" }

            val (processed, bookStoragePath, coverStoragePath) =
                prepareImport(scope, sourcePath, fileName)
            val metadata = processed.metadata
            val edition = processed.edition

            val stagedId = UUID.randomUUID().toString()

            val stagedBook =
                StagedBook(
                    id = stagedId,
                    userId = userId,
                    title = metadata.title,
                    authors = processed.authors,
                    authorSuggestions = emptyMap(),
                    storagePath = bookStoragePath.value,
                    coverPath = coverStoragePath?.value,
                    description = metadata.description,
                    publisher = metadata.publisher,
                    publishYear = metadata.published,
                    genres = metadata.genres,
                    moods = metadata.moods,
                    series =
                        processed.series.map { parsedSeries ->
                            StagedSeries(name = parsedSeries.name, index = parsedSeries.index)
                        },
                    ebookMetadata =
                        if (edition.format == BookFormat.EBOOK)
                            StagedEditionMetadata(
                                storagePath = bookStoragePath.value,
                                fileHash = edition.fileHash,
                                isbn13 = edition.isbn13?.value,
                                isbn10 = edition.isbn10?.value,
                                asin = edition.asin?.value,
                                pages = edition.pages?.toInt(),
                            )
                        else null,
                    audiobookMetadata =
                        if (edition.format == BookFormat.AUDIOBOOK)
                            StagedEditionMetadata(
                                storagePath = bookStoragePath.value,
                                fileHash = edition.fileHash,
                                isbn13 = edition.isbn13?.value,
                                isbn10 = edition.isbn10?.value,
                                asin = edition.asin?.value,
                                narrator = edition.narrator,
                                totalTime = edition.totalTime,
                            )
                        else null,
                    mediaType =
                        if (edition.format == BookFormat.AUDIOBOOK) MediaType.AUDIOBOOK
                        else MediaType.EBOOK,
                    chapters = processed.chapters,
                    size = processed.edition.size,
                    createdAt = Instant.now().toString(),
                )

            logger.info { "Created staged book: $stagedId for title=${metadata.title}" }

            stagedBookStore.put(stagedBook)

            return stagedBook
        }
    }
