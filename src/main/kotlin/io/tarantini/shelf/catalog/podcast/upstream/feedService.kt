@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast.upstream

import arrow.core.raise.context.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.PodcastMutationRepository
import io.tarantini.shelf.catalog.podcast.PodcastReadRepository
import io.tarantini.shelf.catalog.podcast.domain.FeedSyncDecision
import io.tarantini.shelf.catalog.podcast.domain.FeedSyncSnapshot
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.domain.SavedPodcastRoot
import io.tarantini.shelf.catalog.podcast.domain.UpstreamFeedItemParse
import io.tarantini.shelf.catalog.podcast.domain.UpstreamFeedParse
import io.tarantini.shelf.catalog.podcast.domain.UpstreamGuid
import io.tarantini.shelf.catalog.podcast.domain.decideFeedSync
import io.tarantini.shelf.integration.podcast.PodcastCredentialService
import io.tarantini.shelf.integration.podcast.feed.FeedFetchAdapter
import io.tarantini.shelf.integration.podcast.feed.FeedFetchResponse
import io.tarantini.shelf.integration.podcast.feed.FeedParser
import io.tarantini.shelf.integration.podcast.feed.ParsedEpisode
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}

@kotlinx.serialization.Serializable
sealed interface UpstreamCacheResult {
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("updated")
    data class Updated(
        val podcast: SavedPodcastRoot,
        val newEpisodes: Int,
        val removedEpisodes: Int,
    ) : UpstreamCacheResult

    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("not_modified")
    data class NotModified(val podcast: SavedPodcastRoot) : UpstreamCacheResult
}

interface PodcastUpstreamFeedService {
    context(_: RaiseContext)
    suspend fun cacheUpstreamFeed(podcastId: PodcastId): UpstreamCacheResult
}

fun podcastUpstreamFeedService(
    readRepository: PodcastReadRepository,
    mutationRepository: PodcastMutationRepository,
    credentialService: PodcastCredentialService,
    feedFetchAdapter: FeedFetchAdapter,
    feedParser: FeedParser,
): PodcastUpstreamFeedService =
    DefaultPodcastUpstreamFeedService(
        readRepository = readRepository,
        mutationRepository = mutationRepository,
        credentialService = credentialService,
        feedFetchAdapter = feedFetchAdapter,
        feedParser = feedParser,
    )

private class DefaultPodcastUpstreamFeedService(
    private val readRepository: PodcastReadRepository,
    private val mutationRepository: PodcastMutationRepository,
    private val credentialService: PodcastCredentialService,
    private val feedFetchAdapter: FeedFetchAdapter,
    private val feedParser: FeedParser,
) : PodcastUpstreamFeedService {
    context(_: RaiseContext)
    override suspend fun cacheUpstreamFeed(podcastId: PodcastId): UpstreamCacheResult {
        val podcast = mutationRepository.getPodcastById(podcastId)
        val credentials = credentialService.getFeedCredentials(podcastId)
        val response =
            feedFetchAdapter.fetchConditional(
                feedUrl = podcast.feedUrl,
                ifNoneMatch = podcast.upstreamEtag,
                ifModifiedSince = podcast.upstreamLastModified,
                credentials = credentials,
            )
        val now = Clock.System.now()

        return when (response) {
            FeedFetchResponse.NotModified -> {
                val updated =
                    mutationRepository.recordUpstreamUnchanged(
                        id = podcastId,
                        etag = podcast.upstreamEtag,
                        lastModified = podcast.upstreamLastModified,
                        fetchedAt = now,
                    )
                UpstreamCacheResult.NotModified(updated)
            }

            is FeedFetchResponse.Body -> {
                val parse = parseUpstream(response.xml)
                val snapshot =
                    FeedSyncSnapshot(
                        podcastId = podcastId,
                        cachedFeed = readRepository.getCachedFeed(podcastId),
                        knownUpstream = readRepository.listUpstreamEpisodes(podcastId),
                        mappings = readRepository.listMappings(podcastId),
                        hostedEpisodes = readRepository.listEpisodes(podcastId),
                    )
                when (val decision = decideFeedSync(snapshot, parse, now)) {
                    FeedSyncDecision.NoChange -> {
                        val updated =
                            mutationRepository.recordUpstreamUnchanged(
                                id = podcastId,
                                etag = response.etag,
                                lastModified = response.lastModified,
                                fetchedAt = now,
                            )
                        UpstreamCacheResult.NotModified(updated)
                    }

                    is FeedSyncDecision.CacheUpdated -> {
                        val updated =
                            mutationRepository.saveUpstreamFeed(
                                podcastId = podcastId,
                                rawXml = decision.rawXml,
                                channelTitle = decision.channelTitle,
                                etag = response.etag,
                                lastModified = response.lastModified,
                                fetchedAt = decision.fetchedAt,
                                upserts = decision.upserts,
                                removedGuids = decision.removedGuids,
                                newMappings = decision.newMappings,
                            )
                        UpstreamCacheResult.Updated(
                            podcast = updated,
                            newEpisodes = decision.newMappings.size,
                            removedEpisodes = decision.removedGuids.size,
                        )
                    }
                }
            }
        }
    }

    context(_: RaiseContext)
    private suspend fun parseUpstream(rawXml: String): UpstreamFeedParse {
        val parsed = feedParser.parse(rawXml)
        val items = parsed.episodes.mapNotNull { toItem(it) }
        return UpstreamFeedParse(channelTitle = parsed.title, rawXml = rawXml, episodes = items)
    }

    private fun toItem(episode: ParsedEpisode): UpstreamFeedItemParse? =
        either {
                UpstreamFeedItemParse(
                    upstreamGuid = UpstreamGuid(episode.guid),
                    title = episode.title,
                    season = episode.season,
                    episode = episode.episode,
                    publishedAt = episode.publishedAt,
                    upstreamAudioUrl = episode.audioUrl,
                    upstreamAudioSize = null,
                    upstreamAudioMime = null,
                    durationSeconds = episode.duration?.inWholeMilliseconds?.toDouble()?.div(1000.0),
                )
            }
            .fold(
                {
                    logger.debug { "Skipping upstream episode with invalid guid: ${episode.guid}" }
                    null
                },
                { it },
            )
}
