@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.*
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.integration.podcast.PodcastCredentialService
import io.tarantini.shelf.processing.audiobook.probePodcastEpisode
import io.tarantini.shelf.processing.storage.StorageService
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PodcastProvider {
    context(_: RaiseContext)
    suspend fun getPodcasts(): List<PodcastSummary>

    context(_: RaiseContext)
    suspend fun getDashboard(): PodcastDashboard

    context(_: RaiseContext)
    suspend fun getPodcast(id: PodcastId): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun getPodcastAggregate(id: PodcastId): SavedPodcastAggregate

    context(_: RaiseContext)
    suspend fun getEpisodesPage(id: PodcastId, page: Int, size: Int, sortDesc: Boolean): EpisodePage
}

interface PodcastModifier {
    context(_: RaiseContext)
    suspend fun createPodcast(command: CreatePodcastCommand): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun updatePodcast(command: UpdatePodcastCommand): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun rotateToken(id: PodcastId): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun revokeToken(id: PodcastId): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun clearFeedCredentials(id: PodcastId)

    context(_: RaiseContext)
    suspend fun deletePodcast(id: PodcastId)

    context(_: RaiseContext)
    suspend fun reprobeEpisodes(id: PodcastId): PodcastReprobeResult
}

data class PodcastReprobeResult(val updatedCount: Int, val skippedCount: Int)

interface PodcastService : PodcastProvider, PodcastModifier

fun podcastService(
    readRepository: PodcastReadRepository,
    mutationRepository: PodcastMutationRepository,
    credentialService: PodcastCredentialService,
    podcastQueries: PodcastQueries,
    storageService: StorageService,
): PodcastService =
    PodcastAggregateService(
        readRepository,
        mutationRepository,
        credentialService,
        podcastQueries,
        storageService,
    )

private class PodcastAggregateService(
    private val readRepository: PodcastReadRepository,
    private val mutationRepository: PodcastMutationRepository,
    private val credentialService: PodcastCredentialService,
    private val podcastQueries: PodcastQueries,
    private val storageService: StorageService,
) : PodcastService {
    context(_: RaiseContext)
    override suspend fun getPodcasts(): List<PodcastSummary> =
        withContext(Dispatchers.IO) { readRepository.getAllPodcasts() }

    context(_: RaiseContext)
    override suspend fun getDashboard(): PodcastDashboard =
        withContext(Dispatchers.IO) {
            val podcasts = readRepository.getAllPodcasts()
            PodcastDashboard(podcasts = podcasts)
        }

    context(_: RaiseContext)
    override suspend fun getPodcast(id: PodcastId): SavedPodcastRoot =
        withContext(Dispatchers.IO) { readRepository.getPodcastById(id) }

    context(_: RaiseContext)
    override suspend fun getPodcastAggregate(id: PodcastId): SavedPodcastAggregate =
        withContext(Dispatchers.IO) { readRepository.getPodcastAggregateById(id) }

    context(_: RaiseContext)
    override suspend fun getEpisodesPage(
        id: PodcastId,
        page: Int,
        size: Int,
        sortDesc: Boolean,
    ): EpisodePage = readRepository.listEpisodesPaged(id, page, size, sortDesc)

    context(_: RaiseContext)
    override suspend fun createPodcast(command: CreatePodcastCommand): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            mutationRepository.createPodcast(
                PodcastRoot.new(
                    seriesId = command.seriesId,
                    feedUrl = command.feedUrl,
                    feedToken = FeedToken.generate(),
                    autoSanitize = command.autoSanitize,
                    autoFetch = command.autoFetch,
                    fetchIntervalMinutes = command.fetchIntervalMinutes,
                )
            )
        }

    context(_: RaiseContext)
    override suspend fun updatePodcast(command: UpdatePodcastCommand): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            val existing = mutationRepository.getPodcastById(command.id)
            mutationRepository.updateFeedSettings(
                id = command.id,
                autoSanitize = command.autoSanitize ?: existing.autoSanitize,
                autoFetch = command.autoFetch ?: existing.autoFetch,
                fetchIntervalMinutes = command.fetchIntervalMinutes ?: existing.fetchIntervalMinutes,
            )
        }

    context(_: RaiseContext)
    override suspend fun rotateToken(id: PodcastId): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            val newToken = FeedToken.generate()
            val graceExpiresAt =
                kotlin.time.Clock.System.now().plus(kotlin.time.Duration.parse("7d"))
            mutationRepository.rotateToken(id, newToken, graceExpiresAt)
        }

    context(_: RaiseContext)
    override suspend fun revokeToken(id: PodcastId): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            val newToken = FeedToken.generate()
            mutationRepository.revokeToken(id, newToken)
        }

    context(_: RaiseContext)
    override suspend fun clearFeedCredentials(id: PodcastId) {
        withContext(Dispatchers.IO) { credentialService.clearFeedCredentials(id) }
    }

    context(_: RaiseContext)
    override suspend fun deletePodcast(id: PodcastId) {
        withContext(Dispatchers.IO) {
            mutationRepository.getPodcastById(id)
            mutationRepository.deletePodcast(id)
        }
    }

    context(_: RaiseContext)
    override suspend fun reprobeEpisodes(id: PodcastId): PodcastReprobeResult =
        withContext(Dispatchers.IO) {
            mutationRepository.getPodcastById(id)
            val rows = podcastQueries.selectEpisodesForReprobeByPodcastId(id).executeAsList()
            var updated = 0
            var skipped = 0
            for (row in rows) {
                val probe =
                    runCatching { storageService.resolve(row.audio_path) }
                        .getOrNull()
                        ?.let { probePodcastEpisode(it) }
                if (probe == null) {
                    skipped += 1
                } else {
                    podcastQueries.updateEpisodeProbedMetadata(
                        totalTime = probe.totalSeconds,
                        description = probe.description,
                        author = probe.author,
                        publishedAt = probe.publishedAt,
                        episodeId = row.id,
                    )
                    updated += 1
                }
            }
            if (updated > 0) {
                podcastQueries.bumpVersion(id)
            }
            PodcastReprobeResult(updatedCount = updated, skippedCount = skipped)
        }
}
