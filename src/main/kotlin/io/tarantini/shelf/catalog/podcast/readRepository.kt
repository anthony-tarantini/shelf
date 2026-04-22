@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.domain.PodcastSummary
import io.tarantini.shelf.catalog.podcast.domain.SavedPodcastRoot
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.catalog.series.domain.SeriesId
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PodcastReadRepository {
    context(_: RaiseContext)
    suspend fun getPodcastById(id: PodcastId): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun getPodcastBySeriesId(seriesId: SeriesId): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun getPodcastSummaryById(id: PodcastId): PodcastSummary

    context(_: RaiseContext)
    suspend fun getAllPodcasts(): List<PodcastSummary>

    context(_: RaiseContext)
    suspend fun getDuePodcasts(): List<SavedPodcastRoot>

    context(_: RaiseContext)
    suspend fun findByFeedToken(token: FeedToken): SavedPodcastRoot?

    context(_: RaiseContext)
    suspend fun guidExists(podcastId: PodcastId, guid: String): Boolean

    context(_: RaiseContext)
    suspend fun getMaxEpisodeForSeason(podcastId: PodcastId, season: Int): Int
}

fun podcastReadRepository(queries: PodcastQueries): PodcastReadRepository =
    SqlDelightPodcastReadRepository(queries)

private class SqlDelightPodcastReadRepository(private val queries: PodcastQueries) :
    PodcastReadRepository {
    context(_: RaiseContext)
    override suspend fun getPodcastById(id: PodcastId): SavedPodcastRoot =
        withContext(Dispatchers.IO) { queries.getPodcastById(id) }

    context(_: RaiseContext)
    override suspend fun getPodcastBySeriesId(seriesId: SeriesId): SavedPodcastRoot =
        withContext(Dispatchers.IO) { queries.getPodcastBySeriesId(seriesId) }

    context(_: RaiseContext)
    override suspend fun getPodcastSummaryById(id: PodcastId): PodcastSummary =
        withContext(Dispatchers.IO) { queries.getPodcastSummaryById(id) }

    context(_: RaiseContext)
    override suspend fun getAllPodcasts(): List<PodcastSummary> =
        withContext(Dispatchers.IO) { queries.getAllPodcasts() }

    context(_: RaiseContext)
    override suspend fun getDuePodcasts(): List<SavedPodcastRoot> =
        withContext(Dispatchers.IO) { queries.getDuePodcasts() }

    context(_: RaiseContext)
    override suspend fun findByFeedToken(token: FeedToken): SavedPodcastRoot? =
        withContext(Dispatchers.IO) { queries.findByFeedToken(token) }

    context(_: RaiseContext)
    override suspend fun guidExists(podcastId: PodcastId, guid: String): Boolean =
        withContext(Dispatchers.IO) { queries.guidExists(podcastId, guid) }

    context(_: RaiseContext)
    override suspend fun getMaxEpisodeForSeason(podcastId: PodcastId, season: Int): Int =
        withContext(Dispatchers.IO) { queries.getMaxEpisodeForSeason(podcastId, season) }
}
