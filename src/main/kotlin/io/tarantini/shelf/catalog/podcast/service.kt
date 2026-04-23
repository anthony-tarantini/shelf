@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.CreatePodcastCommand
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.domain.PodcastRoot
import io.tarantini.shelf.catalog.podcast.domain.PodcastSummary
import io.tarantini.shelf.catalog.podcast.domain.SavedPodcastAggregate
import io.tarantini.shelf.catalog.podcast.domain.SavedPodcastRoot
import io.tarantini.shelf.catalog.podcast.domain.UpdatePodcastCommand
import io.tarantini.shelf.integration.persistence.CredentialsQueries
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PodcastProvider {
    context(_: RaiseContext)
    suspend fun getPodcasts(): List<PodcastSummary>

    context(_: RaiseContext)
    suspend fun getPodcast(id: PodcastId): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun getPodcastAggregate(id: PodcastId): SavedPodcastAggregate
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
}

interface PodcastService : PodcastProvider, PodcastModifier

fun podcastService(
    readRepository: PodcastReadRepository,
    mutationRepository: PodcastMutationRepository,
): PodcastService = PodcastAggregateService(readRepository, mutationRepository)

fun podcastService(
    queries: io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries,
    credentialsQueries: CredentialsQueries,
): PodcastService =
    podcastService(
        readRepository = podcastReadRepository(queries, credentialsQueries),
        mutationRepository = podcastMutationRepository(queries),
    )

private class PodcastAggregateService(
    private val readRepository: PodcastReadRepository,
    private val mutationRepository: PodcastMutationRepository,
) : PodcastService {
    context(_: RaiseContext)
    override suspend fun getPodcasts(): List<PodcastSummary> =
        withContext(Dispatchers.IO) { readRepository.getAllPodcasts() }

    context(_: RaiseContext)
    override suspend fun getPodcast(id: PodcastId): SavedPodcastRoot =
        withContext(Dispatchers.IO) { readRepository.getPodcastById(id) }

    context(_: RaiseContext)
    override suspend fun getPodcastAggregate(id: PodcastId): SavedPodcastAggregate =
        withContext(Dispatchers.IO) { readRepository.getPodcastAggregateById(id) }

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
            mutationRepository.getPodcastById(id)
            val newToken = FeedToken.generate()
            // 7 days grace period
            val graceExpiresAt =
                kotlin.time.Clock.System.now().plus(kotlin.time.Duration.parse("7d"))
            mutationRepository.rotateToken(id, newToken, graceExpiresAt)
            mutationRepository.getPodcastById(id)
        }

    context(_: RaiseContext)
    override suspend fun revokeToken(id: PodcastId): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            mutationRepository.getPodcastById(id)
            val newToken = FeedToken.generate()
            mutationRepository.revokeToken(id, newToken)
            mutationRepository.getPodcastById(id)
        }

    context(_: RaiseContext)
    override suspend fun clearFeedCredentials(id: PodcastId) {
        withContext(Dispatchers.IO) {
            credentialService.clearFeedCredentials(id)
        }
    }

    context(_: RaiseContext)
    override suspend fun deletePodcast(id: PodcastId) {
        withContext(Dispatchers.IO) {
            mutationRepository.getPodcastById(id)
            mutationRepository.deletePodcast(id)
        }
    }
}
