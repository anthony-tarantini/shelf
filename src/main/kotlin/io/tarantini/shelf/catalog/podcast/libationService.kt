@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.domain.ASIN
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.persistence.MetadataQueries
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.catalog.podcast.domain.LibationScanFailed
import io.tarantini.shelf.catalog.podcast.domain.LibationScanStatus
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import io.tarantini.shelf.integration.persistence.LibationImportQueries
import io.tarantini.shelf.integration.podcast.libation.LibationResolvedManifest
import io.tarantini.shelf.integration.podcast.libation.LibationScanner
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.io.path.extension
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

interface PodcastLibationService {
    suspend fun getStatus(): LibationScanStatus

    context(_: RaiseContext)
    suspend fun scanNow(): LibationScanStatus

    suspend fun scanNowBestEffort(): LibationScanStatus
}

@Suppress("LongParameterList")
fun podcastLibationService(
    enabled: Boolean,
    dropDirectory: String,
    scanner: LibationScanner,
    libationImportQueries: LibationImportQueries,
    seriesQueries: SeriesQueries,
    podcastQueries: PodcastQueries,
    bookQueries: BookQueries,
    metadataQueries: MetadataQueries,
    storageService: StorageService,
): PodcastLibationService =
    DefaultPodcastLibationService(
        enabled = enabled,
        dropDirectory = dropDirectory,
        scanner = scanner,
        libationImportQueries = libationImportQueries,
        seriesQueries = seriesQueries,
        podcastQueries = podcastQueries,
        bookQueries = bookQueries,
        metadataQueries = metadataQueries,
        storageService = storageService,
    )

private class DefaultPodcastLibationService(
    private val enabled: Boolean,
    dropDirectory: String,
    private val scanner: LibationScanner,
    private val libationImportQueries: LibationImportQueries,
    private val seriesQueries: SeriesQueries,
    private val podcastQueries: PodcastQueries,
    private val bookQueries: BookQueries,
    private val metadataQueries: MetadataQueries,
    private val storageService: StorageService,
) : PodcastLibationService {
    private val dropDirectoryPath = Path.of(dropDirectory).toAbsolutePath().normalize()
    private val stateMutex = Mutex()
    private var status = LibationScanStatus(enabled = enabled, running = false)

    override suspend fun getStatus(): LibationScanStatus =
        withContext(Dispatchers.IO) {
            stateMutex.withLock {
                val latestRun = libationImportQueries.selectLatestRun().executeAsOneOrNull()
                if (latestRun != null) {
                    status = statusFromRun(latestRun)
                }
                status
            }
        }

    context(_: RaiseContext)
    override suspend fun scanNow(): LibationScanStatus {
        val (updated, error) = performScanAndImport()
        if (error != null) raise(LibationScanFailed(error.message))
        return updated
    }

    override suspend fun scanNowBestEffort(): LibationScanStatus {
        val (updated, _) = performScanAndImport()
        return updated
    }

    private suspend fun performScanAndImport(): Pair<LibationScanStatus, Throwable?> {
        if (!enabled) return getStatus() to null

        val startedAt = Clock.System.now()
        stateMutex.withLock {
            status =
                status.copy(
                    running = true,
                    startedAt = startedAt,
                    finishedAt = null,
                    lastError = null,
                )
        }

        val runId = withContext(Dispatchers.IO) { libationImportQueries.startRun().executeAsOne() }

        var scanError: Throwable? = null
        val scanResult =
            runCatching { withContext(Dispatchers.IO) { scanner.scan(dropDirectoryPath) } }
                .onFailure { error ->
                    scanError = error
                    logger.warn(error) { "Libation manifest scan failed." }
                }
                .getOrNull()

        val finishedAt = Clock.System.now()
        val updated =
            if (scanResult == null) {
                withContext(Dispatchers.IO) {
                    libationImportQueries.finishRun(
                        id = runId,
                        status = "FAILED",
                        discoveredCount = 0,
                        validManifestCount = 0,
                        invalidManifestCount = 0,
                        importedCreatedCount = 0,
                        importedSkippedCount = 0,
                        importedFailedCount = 0,
                        errorMessage = scanError?.message ?: "Unknown Libation scan error",
                    )
                }

                LibationScanStatus(
                    enabled = true,
                    running = false,
                    lastRunId = runId.toString(),
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    lastScanAt = finishedAt,
                    lastError = scanError?.message ?: "Unknown Libation scan error",
                )
            } else {
                val runSeriesByTitle = mutableMapOf<String, SeriesId>()
                var created = 0
                var skipped = 0
                var failed = 0
                var firstFailureMessage: String? = null

                for (manifest in scanResult.manifests) {
                    when (val outcome = importManifest(manifest, runSeriesByTitle)) {
                        is ImportOutcome.Created -> created += 1
                        is ImportOutcome.Skipped -> skipped += 1
                        is ImportOutcome.Failed -> {
                            failed += 1
                            if (firstFailureMessage == null) {
                                firstFailureMessage = outcome.message
                            }
                        }
                    }
                }

                val runStatus = if (failed > 0) "FAILED" else "COMPLETED"
                withContext(Dispatchers.IO) {
                    libationImportQueries.finishRun(
                        id = runId,
                        status = runStatus,
                        discoveredCount = scanResult.discoveredCount,
                        validManifestCount = scanResult.validManifestCount,
                        invalidManifestCount = scanResult.invalidManifestCount,
                        importedCreatedCount = created,
                        importedSkippedCount = skipped,
                        importedFailedCount = failed,
                        errorMessage = firstFailureMessage,
                    )
                }

                LibationScanStatus(
                    enabled = true,
                    running = false,
                    lastRunId = runId.toString(),
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    lastScanAt = finishedAt,
                    lastSuccessAt = if (failed == 0) finishedAt else null,
                    discoveredCount = scanResult.discoveredCount,
                    validManifestCount = scanResult.validManifestCount,
                    invalidManifestCount = scanResult.invalidManifestCount,
                    importedCreatedCount = created,
                    importedSkippedCount = skipped,
                    importedFailedCount = failed,
                    lastError = firstFailureMessage,
                )
            }

        stateMutex.withLock { status = updated }
        val terminalError =
            when {
                scanError != null -> scanError
                updated.importedFailedCount > 0 ->
                    IllegalStateException(updated.lastError ?: "Libation import failed.")
                else -> null
            }
        return updated to terminalError
    }

    private suspend fun importManifest(
        manifest: LibationResolvedManifest,
        runSeriesByTitle: MutableMap<String, SeriesId>,
    ): ImportOutcome =
        withContext(Dispatchers.IO) {
            val sourceKey = sourceKey(manifest.asin)
            val guid = sourceKey
            val record =
                libationImportQueries.selectRecordBySourceKey(sourceKey).executeAsOneOrNull()

            val existingPodcastId = record?.podcast_id
            if (
                record?.status == "IMPORTED" &&
                    existingPodcastId != null &&
                    guidExists(existingPodcastId, guid)
            ) {
                upsertImportRecord(
                    sourceKey = sourceKey,
                    manifest = manifest,
                    seriesId = record.series_id,
                    podcastId = record.podcast_id,
                    bookId = record.book_id,
                    status = "IMPORTED",
                    lastError = null,
                    firstImportedAt = record.first_imported_at,
                )
                return@withContext ImportOutcome.Skipped
            }

            val seriesId =
                record?.series_id
                    ?: findOrCreateSeriesIdForRun(manifest.seriesTitle, runSeriesByTitle)
            val podcastId = record?.podcast_id ?: createPodcastForSeries(seriesId, manifest.asin)

            if (guidExists(podcastId, guid)) {
                upsertImportRecord(
                    sourceKey = sourceKey,
                    manifest = manifest,
                    seriesId = seriesId,
                    podcastId = podcastId,
                    bookId = record?.book_id,
                    status = "IMPORTED",
                    lastError = null,
                    firstImportedAt =
                        record?.first_imported_at ?: OffsetDateTime.now(ZoneOffset.UTC),
                )
                return@withContext ImportOutcome.Skipped
            }

            val storagePath = episodeAudioStoragePath(seriesId, podcastId, manifest)
            val copied =
                either { storageService.save(storagePath, manifest.audioPath) }
                    .fold({ false }, { true })
            if (!copied) {
                upsertImportRecord(
                    sourceKey = sourceKey,
                    manifest = manifest,
                    seriesId = seriesId,
                    podcastId = podcastId,
                    bookId = record?.book_id,
                    status = "FAILED",
                    lastError = "Failed to copy audio file to managed storage.",
                    firstImportedAt = record?.first_imported_at,
                )
                return@withContext ImportOutcome.Failed(
                    "Failed to copy audio file to managed storage."
                )
            }

            val result = runCatching {
                podcastQueries.transactionWithResult {
                    if (guidExists(podcastId, guid)) {
                        return@transactionWithResult ImportOutcome.Skipped
                    }

                    val bookId =
                        bookQueries.insert(title = manifest.title, coverPath = null).executeAsOne()
                    val claimed =
                        podcastQueries
                            .claimEpisodeGuid(podcastId = podcastId, guid = guid, bookId = bookId)
                            .executeAsOneOrNull()

                    if (claimed == null) {
                        bookQueries.deleteById(bookId).executeAsOneOrNull()
                        return@transactionWithResult ImportOutcome.Skipped
                    }

                    val season = 0
                    val episode =
                        podcastQueries.selectMaxEpisodeForSeason(podcastId, season).executeAsOne() +
                            1

                    podcastQueries.insertEpisodeOrdering(
                        podcastId = podcastId,
                        bookId = bookId,
                        season = season,
                        episode = episode,
                        publishedAt = null,
                    )

                    metadataQueries.insertMetadata(
                        bookId = bookId,
                        title = manifest.title,
                        description = manifest.description,
                        publisher = null,
                        published = manifest.publishedYear,
                        language = null,
                    )

                    metadataQueries.insertEdition(
                        bookId = bookId,
                        format = BookFormat.AUDIOBOOK,
                        path = storagePath,
                        fileHash = null,
                        narrator = null,
                        translator = null,
                        isbn10 = null,
                        isbn13 = null,
                        asin = ASIN.fromRaw(manifest.asin),
                        pages = null,
                        totalTime = manifest.durationSeconds,
                        size = Files.size(manifest.audioPath),
                    )

                    podcastQueries.bumpVersion(podcastId)
                    podcastQueries.updateLastFetched(
                        fetchedAt = Clock.System.now().toOffsetDateTimeUtc(),
                        id = podcastId,
                    )

                    upsertImportRecord(
                        sourceKey = sourceKey,
                        manifest = manifest,
                        seriesId = seriesId,
                        podcastId = podcastId,
                        bookId = bookId,
                        status = "IMPORTED",
                        lastError = null,
                        firstImportedAt =
                            record?.first_imported_at ?: OffsetDateTime.now(ZoneOffset.UTC),
                    )
                    ImportOutcome.Created
                }
            }

            result.getOrElse { error ->
                logger.warn(error) { "Libation manifest import failed." }
                upsertImportRecord(
                    sourceKey = sourceKey,
                    manifest = manifest,
                    seriesId = seriesId,
                    podcastId = podcastId,
                    bookId = record?.book_id,
                    status = "FAILED",
                    lastError = error.message ?: "Unknown import error",
                    firstImportedAt = record?.first_imported_at,
                )
                ImportOutcome.Failed(error.message ?: "Unknown import error")
            }
        }

    private fun upsertImportRecord(
        sourceKey: String,
        manifest: LibationResolvedManifest,
        seriesId: SeriesId?,
        podcastId: PodcastId?,
        bookId: BookId?,
        status: String,
        lastError: String?,
        firstImportedAt: OffsetDateTime?,
    ) {
        libationImportQueries.upsertRecord(
            sourceKey = sourceKey,
            asin = manifest.asin,
            seriesId = seriesId,
            podcastId = podcastId,
            bookId = bookId,
            manifestPath = manifest.manifestPath.toString(),
            audioPath = manifest.audioPath.toString(),
            status = status,
            lastError = lastError,
            firstImportedAt = firstImportedAt,
        )
    }

    private fun findOrCreateSeriesIdForRun(
        seriesTitle: String,
        runSeriesByTitle: MutableMap<String, SeriesId>,
    ): SeriesId =
        runSeriesByTitle.getOrPut(seriesTitle) {
            // Avoid global uniqueness assumptions on series titles.
            seriesQueries.insert(seriesTitle).executeAsOne()
        }

    private fun createPodcastForSeries(seriesId: SeriesId, asin: String): PodcastId =
        podcastQueries
            .insert(
                seriesId = seriesId,
                feedUrl = FeedUrl.fromRaw("https://libation.local/${asin.lowercase()}"),
                feedToken = FeedToken.generate(),
                autoSanitize = true,
                autoFetch = true,
                fetchIntervalMinutes = 1_440,
            )
            .executeAsOne()

    private fun guidExists(podcastId: PodcastId, guid: String): Boolean =
        podcastQueries
            .selectGuidByPodcastAndGuid(podcastId = podcastId, guid = guid)
            .executeAsOneOrNull() != null

    private fun episodeAudioStoragePath(
        seriesId: SeriesId,
        podcastId: PodcastId,
        manifest: LibationResolvedManifest,
    ): StoragePath {
        val extension = manifest.audioPath.extension.lowercase().ifBlank { "m4b" }
        val base = StoragePath.fromRaw("podcasts/${seriesId.value}/${podcastId.value}/episodes")
        val title = StoragePath.safeSegment(manifest.title, "episode")
        val asin = StoragePath.safeSegment(manifest.asin.lowercase(), "asin")
        return base.resolve("$title-$asin.$extension")
    }

    private fun sourceKey(asin: String): String = "libation:${asin.lowercase()}"

    private fun statusFromRun(
        run: io.tarantini.shelf.integration.persistence.Libation_import_runs
    ): LibationScanStatus =
        LibationScanStatus(
            enabled = enabled,
            running = run.status == "RUNNING",
            lastRunId = run.id.toString(),
            startedAt = run.started_at.toKotlinInstant(),
            finishedAt = run.finished_at.toKotlinInstant(),
            lastScanAt = run.finished_at.toKotlinInstant() ?: run.started_at.toKotlinInstant(),
            lastSuccessAt =
                if (run.status == "COMPLETED" && run.imported_failed_count == 0) {
                    run.finished_at.toKotlinInstant()
                } else {
                    null
                },
            discoveredCount = run.discovered_count,
            validManifestCount = run.valid_manifest_count,
            invalidManifestCount = run.invalid_manifest_count,
            importedCreatedCount = run.imported_created_count,
            importedSkippedCount = run.imported_skipped_count,
            importedFailedCount = run.imported_failed_count,
            lastError = run.error_message,
        )
}

private sealed interface ImportOutcome {
    data object Created : ImportOutcome

    data object Skipped : ImportOutcome

    data class Failed(val message: String) : ImportOutcome
}

private fun Instant.toOffsetDateTimeUtc(): OffsetDateTime =
    OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(toEpochMilliseconds()), ZoneOffset.UTC)

private fun OffsetDateTime?.toKotlinInstant(): Instant? =
    this?.let { Instant.fromEpochMilliseconds(it.toInstant().toEpochMilli()) }
