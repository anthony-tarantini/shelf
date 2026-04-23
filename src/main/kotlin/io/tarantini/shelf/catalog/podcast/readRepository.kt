@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.podcast.domain.*
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.integration.persistence.CredentialsQueries
import io.tarantini.shelf.integration.podcast.CredentialType
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PodcastReadRepository {
    context(_: RaiseContext)
    suspend fun getPodcastById(id: PodcastId): SavedPodcastRoot

    context(_: RaiseContext)
    suspend fun getPodcastAggregateById(id: PodcastId): SavedPodcastAggregate

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

    context(_: RaiseContext)
    suspend fun hasGlobalAudibleCredential(): Boolean
}

fun podcastReadRepository(
    queries: PodcastQueries,
    credentialsQueries: CredentialsQueries,
): PodcastReadRepository = SqlDelightPodcastReadRepository(queries, credentialsQueries)

private class SqlDelightPodcastReadRepository(
    private val queries: PodcastQueries,
    private val credentialsQueries: CredentialsQueries,
) : PodcastReadRepository {
    context(_: RaiseContext)
    override suspend fun getPodcastById(id: PodcastId): SavedPodcastRoot =
        withContext(Dispatchers.IO) { queries.getPodcastById(id) }

    context(_: RaiseContext)
    override suspend fun getPodcastAggregateById(id: PodcastId): SavedPodcastAggregate =
        withContext(Dispatchers.IO) {
            val root = queries.getPodcastById(id)
            val summary = queries.getPodcastSummaryById(id)
            val episodes = queries.getEpisodesByPodcastId(id)
            val hasCredentials = credentialsQueries.hasCredentials(id)
            
            val types = credentialsQueries.selectByPodcastId(id).executeAsOneOrNull()?.credential_type
            val hasAudible = types == CredentialType.AUDIBLE_COOKIE.name

            PodcastAggregate(
                podcast = root,
                seriesId = root.seriesId,
                seriesTitle = summary.seriesTitle,
                episodes = episodes,
                credential =
                    if (hasCredentials) CredentialStatus.HAS_CREDENTIAL
                    else CredentialStatus.NO_CREDENTIAL,
                audibleConnected = hasAudible,
                audibleUsername = if (hasAudible) "Connected Account" else null
            )
        }

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

    context(_: RaiseContext)
    override suspend fun hasGlobalAudibleCredential(): Boolean =
        withContext(Dispatchers.IO) { 
            credentialsQueries.existsAnyByType(CredentialType.AUDIBLE_COOKIE.name).executeAsOne()
        }
}
