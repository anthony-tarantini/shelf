@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast.upstream

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.PodcastMutationRepository
import io.tarantini.shelf.catalog.podcast.PodcastReadRepository
import io.tarantini.shelf.catalog.podcast.domain.EpisodeMapping
import io.tarantini.shelf.catalog.podcast.domain.EpisodeMappingMode
import io.tarantini.shelf.catalog.podcast.domain.FeedSyncSnapshot
import io.tarantini.shelf.catalog.podcast.domain.HostedEpisodeAlreadyMapped
import io.tarantini.shelf.catalog.podcast.domain.MappingDecision
import io.tarantini.shelf.catalog.podcast.domain.MappingSuggestion
import io.tarantini.shelf.catalog.podcast.domain.PodcastEpisodeId
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.domain.SavedPodcastRoot
import io.tarantini.shelf.catalog.podcast.domain.UpstreamGuid
import io.tarantini.shelf.catalog.podcast.domain.decideManualMapping
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi

interface PodcastMappingService {
    context(_: RaiseContext)
    suspend fun listUnmatched(podcastId: PodcastId): List<MappingSuggestion>

    context(_: RaiseContext)
    suspend fun setManualMapping(
        podcastId: PodcastId,
        upstreamGuid: UpstreamGuid,
        hostedEpisodeId: PodcastEpisodeId?,
    ): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun clearMapping(podcastId: PodcastId, upstreamGuid: UpstreamGuid): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun attemptAutoMatchForHostedEpisode(
        podcastId: PodcastId,
        hostedEpisodeId: PodcastEpisodeId,
    ): EpisodeMapping?
}

fun podcastMappingService(
    readRepository: PodcastReadRepository,
    mutationRepository: PodcastMutationRepository,
): PodcastMappingService = DefaultPodcastMappingService(readRepository, mutationRepository)

private class DefaultPodcastMappingService(
    private val readRepository: PodcastReadRepository,
    private val mutationRepository: PodcastMutationRepository,
) : PodcastMappingService {
    context(_: RaiseContext)
    override suspend fun listUnmatched(podcastId: PodcastId): List<MappingSuggestion> {
        val mappings = readRepository.listMappings(podcastId)
        val mappedGuids =
            mappings.filter { it.hostedEpisodeId != null }.map { it.upstreamGuid }.toSet()
        val upstream = readRepository.listUpstreamEpisodes(podcastId)
        val hosted = readRepository.listEpisodes(podcastId)
        val claimedHostedIds = mappings.mapNotNull { it.hostedEpisodeId }.toSet()
        val freeHosted = hosted.filter { it.id !in claimedHostedIds }
        return upstream
            .filter { it.upstreamGuid !in mappedGuids }
            .map { up ->
                MappingSuggestion(
                    upstream = up,
                    candidates =
                        freeHosted.filter { h ->
                            up.season != null &&
                                up.episode != null &&
                                h.season == up.season &&
                                h.episode == up.episode
                        },
                )
            }
    }

    context(_: RaiseContext)
    override suspend fun setManualMapping(
        podcastId: PodcastId,
        upstreamGuid: UpstreamGuid,
        hostedEpisodeId: PodcastEpisodeId?,
    ): SavedPodcastRoot {
        val snapshot = loadSnapshot(podcastId)
        val now = Clock.System.now()
        return when (
            val decision = decideManualMapping(snapshot, upstreamGuid, hostedEpisodeId, now)
        ) {
            MappingDecision.NoChange -> mutationRepository.getPodcastById(podcastId)
            is MappingDecision.Cleared ->
                mutationRepository.clearMapping(decision.podcastId, decision.upstreamGuid)

            is MappingDecision.ManualSet -> mutationRepository.upsertMapping(decision.mapping)
            is MappingDecision.Conflict -> raise(HostedEpisodeAlreadyMapped)
        }
    }

    context(_: RaiseContext)
    override suspend fun clearMapping(
        podcastId: PodcastId,
        upstreamGuid: UpstreamGuid,
    ): SavedPodcastRoot = mutationRepository.clearMapping(podcastId, upstreamGuid)

    context(_: RaiseContext)
    override suspend fun attemptAutoMatchForHostedEpisode(
        podcastId: PodcastId,
        hostedEpisodeId: PodcastEpisodeId,
    ): EpisodeMapping? {
        val hosted =
            readRepository.listEpisodes(podcastId).firstOrNull { it.id == hostedEpisodeId }
                ?: return null
        val mappings = readRepository.listMappings(podcastId)
        val claimedHostedIds = mappings.mapNotNull { it.hostedEpisodeId }.toSet()
        if (hostedEpisodeId in claimedHostedIds) return null
        val upstream = readRepository.listUpstreamEpisodes(podcastId)
        val candidate =
            upstream.firstOrNull { up ->
                up.season == hosted.season &&
                    up.episode == hosted.episode &&
                    mappings.none {
                        it.upstreamGuid == up.upstreamGuid && it.hostedEpisodeId != null
                    }
            } ?: return null
        val mapping =
            EpisodeMapping(
                podcastId = podcastId,
                upstreamGuid = candidate.upstreamGuid,
                hostedEpisodeId = hostedEpisodeId,
                mode = EpisodeMappingMode.AUTO_SEASON_EPISODE,
                manualOverride = false,
                updatedAt = Clock.System.now(),
            )
        mutationRepository.upsertMapping(mapping)
        return mapping
    }

    context(_: RaiseContext)
    private suspend fun loadSnapshot(podcastId: PodcastId): FeedSyncSnapshot =
        FeedSyncSnapshot(
            podcastId = podcastId,
            cachedFeed = readRepository.getCachedFeed(podcastId),
            knownUpstream = readRepository.listUpstreamEpisodes(podcastId),
            mappings = readRepository.listMappings(podcastId),
            hostedEpisodes = readRepository.listEpisodes(podcastId),
        )
}
