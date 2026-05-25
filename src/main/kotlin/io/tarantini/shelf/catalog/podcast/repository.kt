@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.EpisodeMapping
import io.tarantini.shelf.catalog.podcast.domain.FeedFlavor
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.NewPodcastRoot
import io.tarantini.shelf.catalog.podcast.domain.PodcastEpisodeId
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.domain.SavedPodcastRoot
import io.tarantini.shelf.catalog.podcast.domain.UpstreamEpisodeRecord
import io.tarantini.shelf.catalog.podcast.domain.UpstreamGuid
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PodcastMutationRepository {
    context(_: RaiseContext)
    suspend fun getPodcastById(id: PodcastId): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun createPodcast(podcast: NewPodcastRoot): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun updateFeedSettings(
        id: PodcastId,
        autoSanitize: Boolean,
        autoFetch: Boolean,
        fetchIntervalMinutes: Int,
    ): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun markFetched(id: PodcastId, fetchedAt: Instant?): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun rotateToken(
        id: PodcastId,
        newToken: FeedToken,
        graceExpiresAt: Instant?,
    ): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun revokeToken(id: PodcastId, newToken: FeedToken): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun bumpVersion(id: PodcastId): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun claimGuid(podcastId: PodcastId, guid: String, episodeId: PodcastEpisodeId): String?

    context(_: RaiseContext)
    suspend fun createEpisode(
        podcastId: PodcastId,
        title: String,
        coverPath: StoragePath?,
        audioPath: StoragePath,
        audioSize: Long,
        totalTime: Double?,
        season: Int,
        episode: Int,
        publishedAt: Instant?,
    ): PodcastEpisodeId

    context(_: RaiseContext)
    suspend fun deletePodcast(id: PodcastId)

    context(_: RaiseContext)
    suspend fun setFeedFlavor(id: PodcastId, feedFlavor: FeedFlavor): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun saveUpstreamFeed(
        podcastId: PodcastId,
        rawXml: String,
        channelTitle: String?,
        etag: String?,
        lastModified: String?,
        fetchedAt: Instant,
        upserts: List<UpstreamEpisodeRecord>,
        removedGuids: List<UpstreamGuid>,
        newMappings: List<EpisodeMapping>,
    ): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun recordUpstreamUnchanged(
        id: PodcastId,
        etag: String?,
        lastModified: String?,
        fetchedAt: Instant,
    ): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun upsertMapping(mapping: EpisodeMapping): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun clearMapping(podcastId: PodcastId, upstreamGuid: UpstreamGuid): SavedPodcastRoot
}

fun podcastMutationRepository(queries: PodcastQueries): PodcastMutationRepository =
    SqlDelightPodcastMutationRepository(queries)

private class SqlDelightPodcastMutationRepository(private val queries: PodcastQueries) :
    PodcastMutationRepository {
    context(_: RaiseContext)
    override suspend fun getPodcastById(id: PodcastId): SavedPodcastRoot =
        withContext(Dispatchers.IO) { queries.getPodcastById(id) }

    context(_: RaiseContext)
    override suspend fun createPodcast(podcast: NewPodcastRoot): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.createPodcast(
                    seriesId = podcast.seriesId,
                    feedUrl = podcast.feedUrl,
                    feedToken = podcast.feedToken,
                    autoSanitize = podcast.autoSanitize,
                    autoFetch = podcast.autoFetch,
                    fetchIntervalMinutes = podcast.fetchIntervalMinutes,
                    feedFlavor = podcast.feedFlavor,
                )
                queries.getPodcastBySeriesId(podcast.seriesId)
            }
        }

    context(_: RaiseContext)
    override suspend fun updateFeedSettings(
        id: PodcastId,
        autoSanitize: Boolean,
        autoFetch: Boolean,
        fetchIntervalMinutes: Int,
    ): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.updateFeedSettings(id, autoSanitize, autoFetch, fetchIntervalMinutes)
                queries.getPodcastById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun markFetched(id: PodcastId, fetchedAt: Instant?): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.markFetched(id, fetchedAt)
                queries.getPodcastById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun rotateToken(
        id: PodcastId,
        newToken: FeedToken,
        graceExpiresAt: Instant?,
    ): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.rotateToken(id = id, newToken = newToken, graceExpiresAt = graceExpiresAt)
                queries.getPodcastById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun revokeToken(id: PodcastId, newToken: FeedToken): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.revokeToken(id = id, newToken = newToken)
                queries.getPodcastById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun bumpVersion(id: PodcastId): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.bumpPodcastVersion(id)
                queries.getPodcastById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun claimGuid(
        podcastId: PodcastId,
        guid: String,
        episodeId: PodcastEpisodeId,
    ): String? = withContext(Dispatchers.IO) { queries.claimGuid(podcastId, guid, episodeId) }

    context(_: RaiseContext)
    override suspend fun createEpisode(
        podcastId: PodcastId,
        title: String,
        coverPath: StoragePath?,
        audioPath: StoragePath,
        audioSize: Long,
        totalTime: Double?,
        season: Int,
        episode: Int,
        publishedAt: Instant?,
    ): PodcastEpisodeId =
        withContext(Dispatchers.IO) {
            queries.createEpisode(
                podcastId = podcastId,
                title = title,
                coverPath = coverPath,
                audioPath = audioPath,
                audioSize = audioSize,
                totalTime = totalTime,
                season = season,
                episode = episode,
                publishedAt = publishedAt,
            )
        }

    context(_: RaiseContext)
    override suspend fun deletePodcast(id: PodcastId) {
        withContext(Dispatchers.IO) { queries.deletePodcast(id) }
    }

    context(_: RaiseContext)
    override suspend fun setFeedFlavor(id: PodcastId, feedFlavor: FeedFlavor): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.setFeedFlavor(id, feedFlavor)
                queries.getPodcastById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun saveUpstreamFeed(
        podcastId: PodcastId,
        rawXml: String,
        channelTitle: String?,
        etag: String?,
        lastModified: String?,
        fetchedAt: Instant,
        upserts: List<UpstreamEpisodeRecord>,
        removedGuids: List<UpstreamGuid>,
        newMappings: List<EpisodeMapping>,
    ): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.persistUpstreamFeed(
                    podcastId = podcastId,
                    rawXml = rawXml,
                    channelTitle = channelTitle,
                    etag = etag,
                    lastModified = lastModified,
                    fetchedAt = fetchedAt,
                    byteSize = rawXml.toByteArray().size.toLong(),
                )
                upserts.forEach { queries.persistUpstreamEpisode(it) }
                removedGuids.forEach { queries.removeUpstreamEpisode(podcastId, it) }
                newMappings.forEach { queries.persistMapping(it) }
                queries.recordUpstreamConditional(podcastId, etag, lastModified, fetchedAt)
                queries.bumpPodcastVersion(podcastId)
                queries.getPodcastById(podcastId)
            }
        }

    context(_: RaiseContext)
    override suspend fun recordUpstreamUnchanged(
        id: PodcastId,
        etag: String?,
        lastModified: String?,
        fetchedAt: Instant,
    ): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.recordUpstreamConditional(id, etag, lastModified, fetchedAt)
                queries.getPodcastById(id)
            }
        }

    context(_: RaiseContext)
    override suspend fun upsertMapping(mapping: EpisodeMapping): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.persistMapping(mapping)
                queries.bumpPodcastVersion(mapping.podcastId)
                queries.getPodcastById(mapping.podcastId)
            }
        }

    context(_: RaiseContext)
    override suspend fun clearMapping(
        podcastId: PodcastId,
        upstreamGuid: UpstreamGuid,
    ): SavedPodcastRoot =
        withContext(Dispatchers.IO) {
            queries.transactionWithResult {
                queries.removeMapping(podcastId, upstreamGuid)
                queries.bumpPodcastVersion(podcastId)
                queries.getPodcastById(podcastId)
            }
        }
}
