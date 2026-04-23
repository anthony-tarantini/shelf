@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast.domain

import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.processing.sanitization.domain.SanitizationStatus
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.ConsistentCopyVisibility
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable

@Serializable
@ConsistentCopyVisibility
data class PodcastRoot<S : PersistenceState>
private constructor(
    val id: Identity<S, PodcastId>,
    val seriesId: SeriesId,
    val feedUrl: FeedUrl,
    val feedToken: FeedToken,
    val feedTokenExpiresAt: Instant?,
    val feedTokenPrevious: FeedToken?,
    val feedTokenPreviousExpiresAt: Instant?,
    val autoSanitize: Boolean,
    val autoFetch: Boolean,
    val lastFetchedAt: Instant?,
    val fetchIntervalMinutes: Int,
    val version: Int,
) {
    fun isTokenValid(token: FeedToken, now: Instant): Boolean =
        (token == feedToken && (feedTokenExpiresAt == null || now < feedTokenExpiresAt)) ||
            (token == feedTokenPrevious &&
                feedTokenPreviousExpiresAt != null &&
                now < feedTokenPreviousExpiresAt)

    companion object {
        fun new(
            seriesId: SeriesId,
            feedUrl: FeedUrl,
            feedToken: FeedToken,
            autoSanitize: Boolean = true,
            autoFetch: Boolean = true,
            fetchIntervalMinutes: Int = 60,
        ) =
            PodcastRoot<PersistenceState.Unsaved>(
                id = Identity.Unsaved,
                seriesId = seriesId,
                feedUrl = feedUrl,
                feedToken = feedToken,
                feedTokenExpiresAt = null,
                feedTokenPrevious = null,
                feedTokenPreviousExpiresAt = null,
                autoSanitize = autoSanitize,
                autoFetch = autoFetch,
                lastFetchedAt = null,
                fetchIntervalMinutes = fetchIntervalMinutes,
                version = 0,
            )

        fun fromRaw(
            id: PodcastId,
            seriesId: SeriesId,
            feedUrl: FeedUrl,
            feedToken: FeedToken,
            feedTokenExpiresAt: Instant?,
            feedTokenPrevious: FeedToken?,
            feedTokenPreviousExpiresAt: Instant?,
            autoSanitize: Boolean,
            autoFetch: Boolean,
            lastFetchedAt: Instant?,
            fetchIntervalMinutes: Int,
            version: Int,
        ) =
            PodcastRoot<PersistenceState.Persisted>(
                id = Identity.Persisted(id),
                seriesId = seriesId,
                feedUrl = feedUrl,
                feedToken = feedToken,
                feedTokenExpiresAt = feedTokenExpiresAt,
                feedTokenPrevious = feedTokenPrevious,
                feedTokenPreviousExpiresAt = feedTokenPreviousExpiresAt,
                autoSanitize = autoSanitize,
                autoFetch = autoFetch,
                lastFetchedAt = lastFetchedAt,
                fetchIntervalMinutes = fetchIntervalMinutes,
                version = version,
            )
    }
}

typealias SavedPodcastRoot = PodcastRoot<PersistenceState.Persisted>

typealias NewPodcastRoot = PodcastRoot<PersistenceState.Unsaved>

@Serializable
data class PodcastDashboard(val podcasts: List<PodcastSummary>, val libation: LibationScanStatus)

@Serializable
data class PodcastAggregate<S : PersistenceState>(
    val podcast: PodcastRoot<S>,
    val seriesId: SeriesId,
    val seriesTitle: String,
    val episodes: List<EpisodeEntry> = emptyList(),
    val credential: CredentialStatus,
)

typealias SavedPodcastAggregate = PodcastAggregate<PersistenceState.Persisted>

typealias NewPodcastAggregate = PodcastAggregate<PersistenceState.Unsaved>

@Serializable
data class PodcastSummary(
    val id: PodcastId,
    val seriesId: SeriesId,
    val seriesTitle: String,
    val feedUrl: FeedUrl,
    val episodeCount: Long,
    val autoSanitize: Boolean,
    val autoFetch: Boolean,
    val lastFetchedAt: Instant?,
    val version: Int,
    val coverPath: StoragePath?,
)

@Serializable
data class EpisodeEntry(
    val bookId: BookId,
    val title: String,
    val season: Int,
    val episode: Int,
    val sanitizationStatus: SanitizationStatus,
    val coverPath: StoragePath?,
    val totalTime: Double?,
    val publishedAt: Instant?,
)

@Serializable
enum class CredentialStatus {
    HAS_CREDENTIAL,
    NO_CREDENTIAL,
}

@Serializable
data class LibationScanStatus(
    val enabled: Boolean,
    val running: Boolean,
    val lastRunId: String? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val lastScanAt: Instant? = null,
    val lastSuccessAt: Instant? = null,
    val discoveredCount: Int = 0,
    val validManifestCount: Int = 0,
    val invalidManifestCount: Int = 0,
    val importedCreatedCount: Int = 0,
    val importedSkippedCount: Int = 0,
    val importedFailedCount: Int = 0,
    val lastError: String? = null,
)
