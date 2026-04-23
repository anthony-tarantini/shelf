@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.toKotlinInstant
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.podcast.domain.EpisodeEntry
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.domain.PodcastNotFound
import io.tarantini.shelf.catalog.podcast.domain.PodcastRoot
import io.tarantini.shelf.catalog.podcast.domain.PodcastSummary
import io.tarantini.shelf.catalog.podcast.domain.SavedPodcastRoot
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.catalog.podcast.persistence.PodcastSummaries
import io.tarantini.shelf.catalog.podcast.persistence.Podcasts
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.processing.sanitization.domain.SanitizationStatus
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

context(_: RaiseContext)
fun PodcastQueries.getAllPodcasts(): List<PodcastSummary> =
    selectAll().executeAsList().map { it.toSummary() }

context(_: RaiseContext)
fun PodcastQueries.getPodcastById(id: PodcastId): SavedPodcastRoot =
    selectById(id).executeAsOneOrNull()?.toRoot() ?: raise(PodcastNotFound)

context(_: RaiseContext)
fun PodcastQueries.getPodcastBySeriesId(seriesId: SeriesId): SavedPodcastRoot =
    selectBySeriesId(seriesId).executeAsOneOrNull()?.toRoot() ?: raise(PodcastNotFound)

context(_: RaiseContext)
fun PodcastQueries.getPodcastSummaryById(id: PodcastId): PodcastSummary =
    selectSummaryById(id).executeAsOneOrNull()?.toSummary() ?: raise(PodcastNotFound)

context(_: RaiseContext)
fun PodcastQueries.findByFeedToken(token: FeedToken): SavedPodcastRoot? =
    selectByFeedTokenIncludingPrevious(token).executeAsOneOrNull()?.toRoot()

context(_: RaiseContext)
fun PodcastQueries.createPodcast(
    seriesId: SeriesId,
    feedUrl: FeedUrl,
    feedToken: FeedToken,
    autoSanitize: Boolean,
    autoFetch: Boolean,
    fetchIntervalMinutes: Int,
): PodcastId =
    insert(
            seriesId = seriesId,
            feedUrl = feedUrl,
            feedToken = feedToken,
            autoSanitize = autoSanitize,
            autoFetch = autoFetch,
            fetchIntervalMinutes = fetchIntervalMinutes,
        )
        .executeAsOne()

context(_: RaiseContext)
fun PodcastQueries.updateFeedSettings(
    id: PodcastId,
    autoSanitize: Boolean,
    autoFetch: Boolean,
    fetchIntervalMinutes: Int,
) {
    updateFeedConfig(
        autoSanitize = autoSanitize,
        autoFetch = autoFetch,
        fetchIntervalMinutes = fetchIntervalMinutes,
        id = id,
    )
}

context(_: RaiseContext)
fun PodcastQueries.markFetched(id: PodcastId, fetchedAt: Instant?) {
    updateLastFetched(fetchedAt.toOffsetDateTimeUtc(), id)
}

context(_: RaiseContext)
fun PodcastQueries.rotateToken(id: PodcastId, newToken: FeedToken, graceExpiresAt: Instant?) {
    rotateToken(graceExpiresAt = graceExpiresAt.toOffsetDateTimeUtc(), newToken = newToken, id = id)
}

context(_: RaiseContext)
fun PodcastQueries.revokeToken(id: PodcastId, newToken: FeedToken) {
    revokeToken(newToken = newToken, id = id)
}

context(_: RaiseContext)
fun PodcastQueries.bumpPodcastVersion(id: PodcastId) {
    bumpVersion(id)
}

context(_: RaiseContext)
fun PodcastQueries.getDuePodcasts(): List<SavedPodcastRoot> =
    selectDuePodcasts().executeAsList().map { it.toRoot() }

context(_: RaiseContext)
fun PodcastQueries.claimGuid(podcastId: PodcastId, guid: String, bookId: BookId): String? =
    claimEpisodeGuid(podcastId = podcastId, guid = guid, bookId = bookId).executeAsOneOrNull()

context(_: RaiseContext)
fun PodcastQueries.guidExists(podcastId: PodcastId, guid: String): Boolean =
    selectGuidByPodcastAndGuid(podcastId = podcastId, guid = guid).executeAsOneOrNull() != null

context(_: RaiseContext)
fun PodcastQueries.getMaxEpisodeForSeason(podcastId: PodcastId, season: Int): Int =
    selectMaxEpisodeForSeason(podcastId, season).executeAsOne()

context(_: RaiseContext)
fun PodcastQueries.createEpisodeOrdering(
    podcastId: PodcastId,
    bookId: BookId,
    season: Int,
    episode: Int,
    publishedAt: Instant?,
) {
    insertEpisodeOrdering(
        podcastId = podcastId,
        bookId = bookId,
        season = season,
        episode = episode,
        publishedAt = publishedAt.toOffsetDateTimeUtc(),
    )
}

context(_: RaiseContext)
fun PodcastQueries.deletePodcast(id: PodcastId) {
    deleteById(id)
}

context(_: RaiseContext)
fun PodcastQueries.getEpisodesByPodcastId(podcastId: PodcastId): List<EpisodeEntry> =
    selectEpisodesByPodcastId(podcastId).executeAsList().map {
        EpisodeEntry(
            bookId = it.book_id,
            title = it.title,
            season = it.season,
            episode = it.episode,
            sanitizationStatus = SanitizationStatus.SKIPPED, // Placeholder until M2
            coverPath = it.cover_path,
            totalTime = it.total_time,
            publishedAt = it.published_at.toKotlinInstant(),
        )
    }

context(_: RaiseContext)
fun io.tarantini.shelf.integration.persistence.CredentialsQueries.hasCredentials(
    podcastId: PodcastId
): Boolean = existsByPodcastId(podcastId).executeAsOne()

private fun PodcastSummaries.toSummary() =
    PodcastSummary(
        id = id,
        seriesId = series_id,
        seriesTitle = series_title,
        feedUrl = feed_url,
        episodeCount = episode_count,
        autoSanitize = auto_sanitize,
        autoFetch = auto_fetch,
        lastFetchedAt = last_fetched_at.toKotlinInstant(),
        version = version,
        coverPath = cover_path,
    )

private fun Podcasts.toRoot() =
    PodcastRoot.fromRaw(
        id = id,
        seriesId = series_id,
        feedUrl = feed_url,
        feedToken = feed_token,
        feedTokenExpiresAt = feed_token_expires_at.toKotlinInstant(),
        feedTokenPrevious = feed_token_previous,
        feedTokenPreviousExpiresAt = feed_token_previous_expires_at.toKotlinInstant(),
        autoSanitize = auto_sanitize,
        autoFetch = auto_fetch,
        lastFetchedAt = last_fetched_at.toKotlinInstant(),
        fetchIntervalMinutes = fetch_interval_minutes,
        version = version,
    )

private fun Instant?.toOffsetDateTimeUtc(): OffsetDateTime? =
    this?.let {
        OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(it.toEpochMilliseconds()),
            ZoneOffset.UTC,
        )
    }
