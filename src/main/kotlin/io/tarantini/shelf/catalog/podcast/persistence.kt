@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.toKotlinInstant
import io.tarantini.shelf.app.toOffsetDateTimeUtc
import io.tarantini.shelf.catalog.podcast.domain.CachedUpstreamFeed
import io.tarantini.shelf.catalog.podcast.domain.EpisodeEntry
import io.tarantini.shelf.catalog.podcast.domain.EpisodeMapping
import io.tarantini.shelf.catalog.podcast.domain.EpisodePage
import io.tarantini.shelf.catalog.podcast.domain.FeedFlavor
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.catalog.podcast.domain.PodcastEpisodeId
import io.tarantini.shelf.catalog.podcast.domain.PodcastId
import io.tarantini.shelf.catalog.podcast.domain.PodcastNotFound
import io.tarantini.shelf.catalog.podcast.domain.PodcastRoot
import io.tarantini.shelf.catalog.podcast.domain.PodcastSummary
import io.tarantini.shelf.catalog.podcast.domain.SavedPodcastRoot
import io.tarantini.shelf.catalog.podcast.domain.UpstreamEpisodeRecord
import io.tarantini.shelf.catalog.podcast.domain.UpstreamGuid
import io.tarantini.shelf.catalog.podcast.persistence.PodcastQueries
import io.tarantini.shelf.catalog.podcast.persistence.PodcastSummaries
import io.tarantini.shelf.catalog.podcast.persistence.Podcasts
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.processing.sanitization.domain.SanitizationStatus
import io.tarantini.shelf.processing.storage.StoragePath
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
    feedFlavor: FeedFlavor = FeedFlavor.PUBLIC_DOWNLOAD,
): PodcastId =
    insert(
            seriesId = seriesId,
            feedUrl = feedUrl,
            feedToken = feedToken,
            autoSanitize = autoSanitize,
            autoFetch = autoFetch,
            fetchIntervalMinutes = fetchIntervalMinutes,
            feedFlavor = feedFlavor,
        )
        .executeAsOne()

context(_: RaiseContext)
fun PodcastQueries.setFeedFlavor(id: PodcastId, feedFlavor: FeedFlavor) {
    updateFeedFlavor(feedFlavor = feedFlavor, id = id)
}

context(_: RaiseContext)
fun PodcastQueries.recordUpstreamConditional(
    id: PodcastId,
    etag: String?,
    lastModified: String?,
    fetchedAt: Instant,
) {
    updateUpstreamConditional(
        etag = etag,
        lastModified = lastModified,
        fetchedAt = fetchedAt.toOffsetDateTimeUtc(),
        id = id,
    )
}

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
fun PodcastQueries.claimGuid(
    podcastId: PodcastId,
    guid: String,
    episodeId: PodcastEpisodeId,
): String? =
    claimEpisodeGuid(podcastId = podcastId, guid = guid, episodeId = episodeId).executeAsOneOrNull()

context(_: RaiseContext)
fun PodcastQueries.guidExists(podcastId: PodcastId, guid: String): Boolean =
    selectGuidByPodcastAndGuid(podcastId = podcastId, guid = guid).executeAsOneOrNull() != null

context(_: RaiseContext)
fun PodcastQueries.getMaxEpisodeForSeason(podcastId: PodcastId, season: Int): Int =
    selectMaxEpisodeForSeason(podcastId, season).executeAsOne()

context(_: RaiseContext)
fun PodcastQueries.createEpisode(
    podcastId: PodcastId,
    title: String,
    coverPath: StoragePath?,
    audioPath: StoragePath,
    audioSize: Long,
    totalTime: Double?,
    season: Int,
    episode: Int,
    publishedAt: Instant?,
    description: String? = null,
    author: String? = null,
): PodcastEpisodeId =
    insertEpisode(
            podcastId = podcastId,
            title = title,
            coverPath = coverPath,
            audioPath = audioPath,
            audioSize = audioSize,
            totalTime = totalTime,
            season = season,
            episode = episode,
            publishedAt = publishedAt.toOffsetDateTimeUtc(),
            description = description,
            author = author,
        )
        .executeAsOne()

context(_: RaiseContext)
fun PodcastQueries.deletePodcast(id: PodcastId) {
    deleteById(id)
}

context(_: RaiseContext)
fun PodcastQueries.getEpisodesByPodcastId(podcastId: PodcastId): List<EpisodeEntry> =
    selectEpisodesByPodcastId(podcastId).executeAsList().map {
        EpisodeEntry(
            id = it.id,
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
fun PodcastQueries.getEpisodesPagedByPodcastId(
    podcastId: PodcastId,
    page: Int,
    size: Int,
    sortDesc: Boolean,
): EpisodePage {
    val safeSize = size.coerceIn(1, 200)
    val safePage = page.coerceAtLeast(0)
    val offset = safePage.toLong() * safeSize.toLong()
    val rows =
        if (sortDesc) {
            selectEpisodesByPodcastIdPagedDesc(
                    podcastId = podcastId,
                    limit = safeSize.toLong(),
                    offset = offset,
                )
                .executeAsList()
                .map {
                    EpisodeEntry(
                        id = it.id,
                        title = it.title,
                        season = it.season,
                        episode = it.episode,
                        sanitizationStatus = SanitizationStatus.SKIPPED,
                        coverPath = it.cover_path,
                        totalTime = it.total_time,
                        publishedAt = it.published_at.toKotlinInstant(),
                    )
                }
        } else {
            selectEpisodesByPodcastIdPagedAsc(
                    podcastId = podcastId,
                    limit = safeSize.toLong(),
                    offset = offset,
                )
                .executeAsList()
                .map {
                    EpisodeEntry(
                        id = it.id,
                        title = it.title,
                        season = it.season,
                        episode = it.episode,
                        sanitizationStatus = SanitizationStatus.SKIPPED,
                        coverPath = it.cover_path,
                        totalTime = it.total_time,
                        publishedAt = it.published_at.toKotlinInstant(),
                    )
                }
        }
    val total = countEpisodesByPodcastId(podcastId).executeAsOne()
    return EpisodePage(items = rows, totalCount = total, page = safePage, size = safeSize)
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

context(_: RaiseContext)
fun PodcastQueries.getUpstreamFeed(podcastId: PodcastId): CachedUpstreamFeed? =
    selectUpstreamFeed(podcastId).executeAsOneOrNull()?.let {
        CachedUpstreamFeed(
            podcastId = it.podcast_id,
            channelTitle = it.channel_title,
            upstreamEtag = it.upstream_etag,
            upstreamLastModified = it.upstream_last_modified,
            fetchedAt = it.fetched_at.toKotlinInstant() ?: Instant.fromEpochMilliseconds(0),
            byteSize = it.byte_size,
        )
    }

context(_: RaiseContext)
fun PodcastQueries.getUpstreamRawXml(podcastId: PodcastId): String? =
    selectUpstreamFeed(podcastId).executeAsOneOrNull()?.raw_xml

context(_: RaiseContext)
fun PodcastQueries.persistUpstreamFeed(
    podcastId: PodcastId,
    rawXml: String,
    channelTitle: String?,
    etag: String?,
    lastModified: String?,
    fetchedAt: Instant,
    byteSize: Long,
) {
    upsertUpstreamFeed(
        podcastId = podcastId,
        rawXml = rawXml,
        channelTitle = channelTitle,
        etag = etag,
        lastModified = lastModified,
        fetchedAt = fetchedAt.toOffsetDateTimeUtc(),
        byteSize = byteSize,
    )
}

context(_: RaiseContext)
fun PodcastQueries.getUpstreamEpisodes(podcastId: PodcastId): List<UpstreamEpisodeRecord> =
    selectUpstreamEpisodes(podcastId).executeAsList().map {
        UpstreamEpisodeRecord(
            podcastId = it.podcast_id,
            upstreamGuid = it.upstream_guid,
            title = it.title,
            season = it.season,
            episode = it.episode,
            publishedAt = it.published_at.toKotlinInstant(),
            upstreamAudioUrl = it.upstream_audio_url,
            upstreamAudioSize = it.upstream_audio_size,
            upstreamAudioMime = it.upstream_audio_mime,
            durationSeconds = it.duration_seconds,
            firstSeenAt = it.first_seen_at.toKotlinInstant() ?: Instant.fromEpochMilliseconds(0),
            lastSeenAt = it.last_seen_at.toKotlinInstant() ?: Instant.fromEpochMilliseconds(0),
        )
    }

context(_: RaiseContext)
fun PodcastQueries.persistUpstreamEpisode(episode: UpstreamEpisodeRecord) {
    upsertUpstreamEpisode(
        podcastId = episode.podcastId,
        upstreamGuid = episode.upstreamGuid,
        title = episode.title,
        season = episode.season,
        episode = episode.episode,
        publishedAt = episode.publishedAt.toOffsetDateTimeUtc(),
        upstreamAudioUrl = episode.upstreamAudioUrl,
        upstreamAudioSize = episode.upstreamAudioSize,
        upstreamAudioMime = episode.upstreamAudioMime,
        durationSeconds = episode.durationSeconds,
        firstSeenAt = episode.firstSeenAt.toOffsetDateTimeUtc(),
        lastSeenAt = episode.lastSeenAt.toOffsetDateTimeUtc(),
    )
}

context(_: RaiseContext)
fun PodcastQueries.removeUpstreamEpisode(podcastId: PodcastId, upstreamGuid: UpstreamGuid) {
    deleteUpstreamEpisode(podcastId = podcastId, upstreamGuid = upstreamGuid)
}

context(_: RaiseContext)
fun PodcastQueries.getMappings(podcastId: PodcastId): List<EpisodeMapping> =
    selectMappings(podcastId).executeAsList().map { it.toMapping() }

context(_: RaiseContext)
fun PodcastQueries.findMapping(podcastId: PodcastId, upstreamGuid: UpstreamGuid): EpisodeMapping? =
    selectMappingByGuid(podcastId = podcastId, upstreamGuid = upstreamGuid)
        .executeAsOneOrNull()
        ?.toMapping()

context(_: RaiseContext)
fun PodcastQueries.persistMapping(mapping: EpisodeMapping) {
    upsertMapping(
        podcastId = mapping.podcastId,
        upstreamGuid = mapping.upstreamGuid,
        hostedEpisodeId = mapping.hostedEpisodeId,
        mode = mapping.mode,
        manualOverride = mapping.manualOverride,
        updatedAt = mapping.updatedAt.toOffsetDateTimeUtc(),
    )
}

context(_: RaiseContext)
fun PodcastQueries.removeMapping(podcastId: PodcastId, upstreamGuid: UpstreamGuid) {
    deleteMapping(podcastId = podcastId, upstreamGuid = upstreamGuid)
}

private fun io.tarantini.shelf.catalog.podcast.persistence.Podcast_episode_mappings.toMapping() =
    EpisodeMapping(
        podcastId = podcast_id,
        upstreamGuid = upstream_guid,
        hostedEpisodeId = hosted_episode_id,
        mode = mode,
        manualOverride = manual_override,
        updatedAt = updated_at.toKotlinInstant() ?: Instant.fromEpochMilliseconds(0),
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
        feedFlavor = feed_flavor,
        upstreamEtag = upstream_etag,
        upstreamLastModified = upstream_last_modified,
        upstreamFetchedAt = upstream_fetched_at.toKotlinInstant(),
    )
