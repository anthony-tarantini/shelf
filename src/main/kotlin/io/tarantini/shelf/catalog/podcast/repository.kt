@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.NewPodcastRoot
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.domain.SavedPodcastRoot
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
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
    suspend fun claimGuid(podcastId: PodcastId, guid: String, bookId: BookId): String?

    context(_: RaiseContext)
    suspend fun createEpisodeOrdering(
        podcastId: PodcastId,
        bookId: BookId,
        season: Int,
        episode: Int,
        publishedAt: Instant?,
    )

    context(_: RaiseContext)
    suspend fun deletePodcast(id: PodcastId)
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
    override suspend fun claimGuid(podcastId: PodcastId, guid: String, bookId: BookId): String? =
        withContext(Dispatchers.IO) { queries.claimGuid(podcastId, guid, bookId) }

    context(_: RaiseContext)
    override suspend fun createEpisodeOrdering(
        podcastId: PodcastId,
        bookId: BookId,
        season: Int,
        episode: Int,
        publishedAt: Instant?,
    ) {
        withContext(Dispatchers.IO) {
            queries.createEpisodeOrdering(
                podcastId = podcastId,
                bookId = bookId,
                season = season,
                episode = episode,
                publishedAt = publishedAt,
            )
        }
    }

    context(_: RaiseContext)
    override suspend fun deletePodcast(id: PodcastId) {
        withContext(Dispatchers.IO) { queries.deletePodcast(id) }
    }
}
