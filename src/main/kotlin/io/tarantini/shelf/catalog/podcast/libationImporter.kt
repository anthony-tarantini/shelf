@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.context.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.app.toOffsetDateTimeUtc
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.catalog.podcast.domain.PodcastEpisodeId
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import io.tarantini.shelf.integration.persistence.LibationImportQueries
import io.tarantini.shelf.integration.podcast.libation.LibationResolvedManifest
import io.tarantini.shelf.processing.audiobook.ffmpeg
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import java.nio.file.Files
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

class LibationManifestImporter(
    private val libationImportQueries: LibationImportQueries,
    private val seriesQueries: SeriesQueries,
    private val podcastQueries: PodcastQueries,
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

            val seriesId =
                record?.series_id
                    ?: findOrCreateSeriesIdForRun(manifest.seriesTitle, runSeriesByTitle)
            val podcastId = record?.podcast_id ?: findOrCreatePodcastForSeries(seriesId)

            // Idempotency: if guid is already claimed, update record and optionally backfill cover.
            val existingEpisodeId =
                record?.episode_id
                    ?: podcastQueries
                        .selectEpisodeIdByPodcastAndGuid(podcastId = podcastId, guid = guid)
                        .executeAsOneOrNull()
            if (existingEpisodeId != null) {
                backfillCoverIfMissing(
                    episodeId = existingEpisodeId,
                    seriesId = seriesId,
                    podcastId = podcastId,
                    manifest = manifest,
                )
                upsertImportRecord(
                    sourceKey = sourceKey,
                    manifest = manifest,
                    seriesId = seriesId,
                    podcastId = podcastId,
                    episodeId = existingEpisodeId,
                    status = "IMPORTED",
                    lastError = null,
                    firstImportedAt =
                        record?.first_imported_at ?: OffsetDateTime.now(ZoneOffset.UTC),
                )
                return@withContext LibationImportOutcome.Skipped
            }

            val audioPath = episodeAudioStoragePath(seriesId, podcastId, manifest)
            val copied =
                either { storageService.save(audioPath, manifest.audioPath) }
                    .fold({ false }, { true })
            if (!copied) {
                upsertImportRecord(
                    sourceKey = sourceKey,
                    manifest = manifest,
                    seriesId = seriesId,
                    podcastId = podcastId,
                    episodeId = record?.episode_id,
                    status = "FAILED",
                    lastError = "Failed to copy audio file to managed storage.",
                    firstImportedAt = record?.first_imported_at,
                )
                return@withContext LibationImportOutcome.Failed(
                    "Failed to copy audio file to managed storage."
                )
            }
            val coverPath = extractAndPersistCover(seriesId, podcastId, manifest)

            val result = runCatching {
                podcastQueries.transactionWithResult {
                    // Re-check idempotency under transaction.
                    val claimedEpisodeId =
                        podcastQueries
                            .selectEpisodeIdByPodcastAndGuid(podcastId = podcastId, guid = guid)
                            .executeAsOneOrNull()
                    if (claimedEpisodeId != null) {
                        return@transactionWithResult LibationImportOutcome.Skipped
                    }

                    val season = 0
                    val nextEpisode =
                        podcastQueries.selectMaxEpisodeForSeason(podcastId, season).executeAsOne() +
                            1
                    val episodeId =
                        podcastQueries
                            .insertEpisode(
                                podcastId = podcastId,
                                title = manifest.title,
                                coverPath = coverPath,
                                audioPath = audioPath,
                                audioSize = Files.size(manifest.audioPath),
                                totalTime = manifest.durationSeconds,
                                season = season,
                                episode = nextEpisode,
                                publishedAt = null,
                            )
                            .executeAsOne()

                    val claimed =
                        podcastQueries
                            .claimEpisodeGuid(
                                podcastId = podcastId,
                                guid = guid,
                                episodeId = episodeId,
                            )
                            .executeAsOneOrNull()
                    if (claimed == null) {
                        podcastQueries.deleteEpisodeById(episodeId)
                        return@transactionWithResult LibationImportOutcome.Skipped
                    }

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
                        episodeId = episodeId,
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
                    episodeId = record?.episode_id,
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
        episodeId: PodcastEpisodeId?,
        status: String,
        lastError: String?,
        firstImportedAt: OffsetDateTime?,
    ) {
        libationImportQueries.upsertRecord(
            sourceKey = sourceKey,
            asin = manifest.asin,
            seriesId = seriesId,
            podcastId = podcastId,
            episodeId = episodeId,
            bookId = null,
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

    private fun findOrCreatePodcastForSeries(seriesId: SeriesId): PodcastId =
        podcastQueries.selectBySeriesId(seriesId).executeAsOneOrNull()?.id
            ?: podcastQueries
                .insert(
                    seriesId = seriesId,
                    feedUrl = FeedUrl.fromRaw("https://libation.local/series/${seriesId.value}"),
                    feedToken = FeedToken.generate(),
                    autoSanitize = true,
                    autoFetch = false,
                    fetchIntervalMinutes = 1_440,
                )
                .executeAsOne()

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

    private suspend fun extractAndPersistCover(
        seriesId: SeriesId,
        podcastId: PodcastId,
        manifest: LibationResolvedManifest,
    ): StoragePath? =
        withContext(Dispatchers.IO) {
            val tempCover =
                extractEmbeddedCoverToTemp(manifest.audioPath) ?: return@withContext null
            val coverPath = episodeCoverStoragePath(seriesId, podcastId, manifest)
            try {
                val saved =
                    either { storageService.save(coverPath, tempCover) }.fold({ false }, { true })
                if (!saved) {
                    logger.warn { "Failed to persist Libation embedded cover to managed storage." }
                    return@withContext null
                }
                // Thumbnail generation is optional for import success.
                either { storageService.generateThumbnail(coverPath) }
                coverPath
            } finally {
                runCatching { tempCover.deleteIfExists() }
            }
        }

    private suspend fun backfillCoverIfMissing(
        episodeId: PodcastEpisodeId?,
        seriesId: SeriesId?,
        podcastId: PodcastId?,
        manifest: LibationResolvedManifest,
    ) {
        if (episodeId == null || seriesId == null || podcastId == null) return
        withContext(Dispatchers.IO) {
            val existing =
                podcastQueries.selectEpisodeById(episodeId).executeAsOneOrNull()
                    ?: return@withContext
            if (existing.cover_path != null) return@withContext
            val coverPath =
                extractAndPersistCover(seriesId, podcastId, manifest) ?: return@withContext
            podcastQueries.updateEpisodeCoverPath(coverPath = coverPath, episodeId = episodeId)
        }
    }

    private fun extractEmbeddedCoverToTemp(audioPath: java.nio.file.Path): java.nio.file.Path? {
        val tempCover = Files.createTempFile("shelf-libation-cover-", ".jpg")
        val process =
            runCatching { ffmpeg(audioPath, tempCover).start() }
                .getOrElse {
                    runCatching { tempCover.deleteIfExists() }
                    logger.debug { "Libation cover extraction skipped: ffmpeg unavailable." }
                    return null
                }

        return try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                runCatching { tempCover.deleteIfExists() }
                logger.debug { "Libation cover extraction timed out for ${audioPath.pathString}." }
                null
            } else if (process.exitValue() == 0 && Files.size(tempCover) > 0) {
                tempCover
            } else {
                runCatching { tempCover.deleteIfExists() }
                null
            }
        } catch (_: Exception) {
            runCatching { tempCover.deleteIfExists() }
            null
        }
    }

    private fun episodeCoverStoragePath(
        seriesId: SeriesId,
        podcastId: PodcastId,
        manifest: LibationResolvedManifest,
    ): StoragePath {
        val base = StoragePath.fromRaw("podcasts/${seriesId.value}/${podcastId.value}/episodes")
        val title = StoragePath.safeSegment(manifest.title, "episode")
        val asin = StoragePath.safeSegment(manifest.asin.lowercase(), "asin")
        return base.resolve("$title-$asin-cover.jpg")
    }

    private fun sourceKey(asin: String): String = "libation:${asin.lowercase()}"
}

sealed interface LibationImportOutcome {
    data object Created : LibationImportOutcome

    data object Skipped : LibationImportOutcome

    data class Failed(val message: String) : LibationImportOutcome
}
