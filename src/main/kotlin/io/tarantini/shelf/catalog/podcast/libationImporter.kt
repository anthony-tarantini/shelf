@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.context.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.domain.ASIN
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.persistence.MetadataQueries
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import io.tarantini.shelf.integration.persistence.LibationImportQueries
import io.tarantini.shelf.integration.podcast.libation.LibationResolvedManifest
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import java.nio.file.Files
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.io.path.extension
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

class LibationManifestImporter(
    private val libationImportQueries: LibationImportQueries,
    private val seriesQueries: SeriesQueries,
    private val podcastQueries: PodcastQueries,
    private val bookQueries: BookQueries,
    private val metadataQueries: MetadataQueries,
    private val storageService: StorageService,
) {
    suspend fun importManifest(
        manifest: LibationResolvedManifest,
        runSeriesByTitle: MutableMap<String, SeriesId>,
    ): LibationImportOutcome =
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
                return@withContext LibationImportOutcome.Skipped
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
                return@withContext LibationImportOutcome.Skipped
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
                return@withContext LibationImportOutcome.Failed(
                    "Failed to copy audio file to managed storage."
                )
            }

            val result = runCatching {
                podcastQueries.transactionWithResult {
                    if (guidExists(podcastId, guid)) {
                        return@transactionWithResult LibationImportOutcome.Skipped
                    }

                    val bookId =
                        bookQueries.insert(title = manifest.title, coverPath = null).executeAsOne()
                    val claimed =
                        podcastQueries
                            .claimEpisodeGuid(podcastId = podcastId, guid = guid, bookId = bookId)
                            .executeAsOneOrNull()

                    if (claimed == null) {
                        bookQueries.deleteById(bookId).executeAsOneOrNull()
                        return@transactionWithResult LibationImportOutcome.Skipped
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
                    LibationImportOutcome.Created
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
                LibationImportOutcome.Failed(error.message ?: "Unknown import error")
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
}

sealed interface LibationImportOutcome {
    data object Created : LibationImportOutcome

    data object Skipped : LibationImportOutcome

    data class Failed(val message: String) : LibationImportOutcome
}

private fun Instant.toOffsetDateTimeUtc(): OffsetDateTime =
    OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(toEpochMilliseconds()), ZoneOffset.UTC)
