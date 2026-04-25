@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.context.raise
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.toKotlinInstant
import io.tarantini.shelf.catalog.podcast.domain.LibationScanFailed
import io.tarantini.shelf.catalog.podcast.domain.LibationScanStatus
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import io.tarantini.shelf.integration.persistence.LibationImportQueries
import io.tarantini.shelf.integration.podcast.libation.LibationScanner
import io.tarantini.shelf.processing.storage.StorageService
import java.nio.file.Path
import kotlin.time.Clock
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
    storageService: StorageService,
): PodcastLibationService =
    DefaultPodcastLibationService(
        enabled = enabled,
        dropDirectory = dropDirectory,
        scanner = scanner,
        libationImportQueries = libationImportQueries,
        importer =
            LibationManifestImporter(
                libationImportQueries = libationImportQueries,
                seriesQueries = seriesQueries,
                podcastQueries = podcastQueries,
                storageService = storageService,
            ),
    )

private class DefaultPodcastLibationService(
    private val enabled: Boolean,
    dropDirectory: String,
    private val scanner: LibationScanner,
    private val libationImportQueries: LibationImportQueries,
    private val importer: LibationManifestImporter,
) : PodcastLibationService {
    private val dropDirectoryPath = Path.of(dropDirectory).toAbsolutePath().normalize()
    private val stateMutex = Mutex()
    private val scanMutex = Mutex()
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
        return scanMutex.withLock { performScanAndImportLocked() }
    }

    private suspend fun performScanAndImportLocked(): Pair<LibationScanStatus, Throwable?> {
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
                    when (val outcome = importer.importManifest(manifest, runSeriesByTitle)) {
                        is LibationImportOutcome.Created -> created += 1
                        is LibationImportOutcome.Skipped -> skipped += 1
                        is LibationImportOutcome.Failed -> {
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
