@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.context.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.app.toOffsetDateTimeUtc
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.domain.SavedPodcastRoot
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.integration.podcast.PodcastCredentialService
import io.tarantini.shelf.integration.podcast.feed.EpisodeAudioFetchAdapter
import io.tarantini.shelf.integration.podcast.feed.FeedFetchAdapter
import io.tarantini.shelf.integration.podcast.feed.FeedParser
import io.tarantini.shelf.integration.podcast.feed.ParsedEpisode
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

interface PodcastFeedFetchService {
    context(_: RaiseContext)
    suspend fun fetchPodcast(podcastId: PodcastId)

    context(_: RaiseContext)
    suspend fun getDuePodcasts(): List<SavedPodcastRoot>
}

fun podcastFeedFetchService(
    readRepository: PodcastReadRepository,
    mutationRepository: PodcastMutationRepository,
    podcastQueries: PodcastQueries,
    storageService: StorageService,
    credentialService: PodcastCredentialService,
    feedFetchAdapter: FeedFetchAdapter,
    feedParser: FeedParser,
    audioFetchAdapter: EpisodeAudioFetchAdapter,
): PodcastFeedFetchService =
    DefaultPodcastFeedFetchService(
        readRepository = readRepository,
        mutationRepository = mutationRepository,
        podcastQueries = podcastQueries,
        storageService = storageService,
        credentialService = credentialService,
        feedFetchAdapter = feedFetchAdapter,
        feedParser = feedParser,
        audioFetchAdapter = audioFetchAdapter,
    )

private class DefaultPodcastFeedFetchService(
    private val readRepository: PodcastReadRepository,
    private val mutationRepository: PodcastMutationRepository,
    private val podcastQueries: PodcastQueries,
    private val storageService: StorageService,
    private val credentialService: PodcastCredentialService,
    private val feedFetchAdapter: FeedFetchAdapter,
    private val feedParser: FeedParser,
    private val audioFetchAdapter: EpisodeAudioFetchAdapter,
) : PodcastFeedFetchService {
    context(_: RaiseContext)
    override suspend fun getDuePodcasts(): List<SavedPodcastRoot> = readRepository.getDuePodcasts()

    context(_: RaiseContext)
    override suspend fun fetchPodcast(podcastId: PodcastId) {
        val podcast = mutationRepository.getPodcastById(podcastId)
        val credentials = credentialService.getFeedCredentials(podcast.id.id)
        val xml = feedFetchAdapter.fetch(podcast.feedUrl, credentials)
        val parsed = feedParser.parse(xml)

        var ingested = 0
        parsed.episodes.forEach { episode ->
            val result = either { ingestEpisode(podcast, episode) }
            result.fold(
                { err ->
                    logger.warn { "Podcast episode ingestion skipped: ${err::class.simpleName}" }
                },
                { inserted -> if (inserted) ingested += 1 },
            )
        }

        mutationRepository.markFetched(podcast.id.id, Clock.System.now())
        if (ingested > 0) {
            mutationRepository.bumpVersion(podcast.id.id)
        }
    }

    context(_: RaiseContext)
    private suspend fun ingestEpisode(podcast: SavedPodcastRoot, episode: ParsedEpisode): Boolean {
        if (readRepository.guidExists(podcast.id.id, episode.guid)) {
            return false
        }

        val downloaded = audioFetchAdapter.fetch(episode.audioUrl)
        var savedPath: StoragePath? = null
        try {
            val path = episodeAudioStoragePath(podcast, episode, downloaded.extension)
            storageService.save(path, downloaded.path)
            savedPath = path

            val inserted =
                withContext(Dispatchers.IO) {
                    podcastQueries.transactionWithResult {
                        val season = episode.season ?: 0
                        val maxEpisodeForSeason =
                            podcastQueries
                                .selectMaxEpisodeForSeason(podcast.id.id, season)
                                .executeAsOne()
                        val resolvedEpisodeNumber =
                            when (val provided = episode.episode) {
                                null -> maxEpisodeForSeason + 1
                                else ->
                                    if (provided <= maxEpisodeForSeason) maxEpisodeForSeason + 1
                                    else provided
                            }

                        val episodeId =
                            podcastQueries
                                .insertEpisode(
                                    podcastId = podcast.id.id,
                                    title = episode.title,
                                    coverPath = null,
                                    audioPath = path,
                                    audioSize = downloaded.size,
                                    totalTime =
                                        episode.duration
                                            ?.inWholeMilliseconds
                                            ?.toDouble()
                                            ?.div(1000.0),
                                    season = season,
                                    episode = resolvedEpisodeNumber,
                                    publishedAt = episode.publishedAt.toOffsetDateTimeUtc(),
                                )
                                .executeAsOne()
                        val claimed =
                            podcastQueries
                                .claimEpisodeGuid(
                                    podcastId = podcast.id.id,
                                    guid = episode.guid,
                                    episodeId = episodeId,
                                )
                                .executeAsOneOrNull()
                        if (claimed == null) {
                            podcastQueries.deleteEpisodeById(episodeId)
                            return@transactionWithResult false
                        }

                        true
                    }
                }

            if (!inserted) {
                storageService.delete(path)
                savedPath = null
            }

            return inserted
        } catch (e: Exception) {
            savedPath?.let { runCatching { storageService.delete(it) } }
            throw e
        } finally {
            runCatching { Files.deleteIfExists(downloaded.path) }
        }
    }

    private fun episodeAudioStoragePath(
        podcast: SavedPodcastRoot,
        episode: ParsedEpisode,
        extension: String,
    ): StoragePath {
        val base =
            StoragePath.fromRaw(
                "podcasts/${podcast.seriesId.value}/${podcast.id.id.value}/episodes"
            )
        val title = StoragePath.safeSegment(episode.title, "episode")
        val guidHash = guidHash(episode.guid)
        return base.resolve("$title-$guidHash.$extension")
    }
}

private fun guidHash(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.take(8).joinToString("") { "%02x".format(it) }
}
