package io.tarantini.shelf.catalog.podcast.domain

import kotlin.time.Instant

data class UpstreamFeedParse(
    val channelTitle: String?,
    val rawXml: String,
    val episodes: List<UpstreamFeedItemParse>,
)

data class UpstreamFeedItemParse(
    val upstreamGuid: UpstreamGuid,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val publishedAt: Instant?,
    val upstreamAudioUrl: String,
    val upstreamAudioSize: Long?,
    val upstreamAudioMime: String?,
    val durationSeconds: Double?,
)

data class FeedSyncSnapshot(
    val podcastId: PodcastId,
    val cachedFeed: CachedUpstreamFeed?,
    val knownUpstream: List<UpstreamEpisodeRecord>,
    val mappings: List<EpisodeMapping>,
    val hostedEpisodes: List<EpisodeEntry>,
)

sealed interface FeedSyncDecision {
    data object NoChange : FeedSyncDecision

    data class CacheUpdated(
        val rawXml: String,
        val channelTitle: String?,
        val byteSize: Long,
        val fetchedAt: Instant,
        val upserts: List<UpstreamEpisodeRecord>,
        val removedGuids: List<UpstreamGuid>,
        val newMappings: List<EpisodeMapping>,
    ) : FeedSyncDecision
}

sealed interface MatchResult {
    data class Matched(val hostedEpisodeId: PodcastEpisodeId, val mode: EpisodeMappingMode) :
        MatchResult

    data object NoMatch : MatchResult
}

sealed interface MappingDecision {
    data object NoChange : MappingDecision

    data class ManualSet(val mapping: EpisodeMapping) : MappingDecision

    data class Cleared(val podcastId: PodcastId, val upstreamGuid: UpstreamGuid) : MappingDecision

    data class Conflict(val existingUpstreamGuid: UpstreamGuid) : MappingDecision
}

/**
 * Pure decision: given current snapshot + freshly parsed feed, produce upserts/removals/mappings.
 * Conditional GET (304) is decided outside via etag; this assumes parsed payload is present.
 */
fun decideFeedSync(
    snapshot: FeedSyncSnapshot,
    parsed: UpstreamFeedParse,
    now: Instant,
): FeedSyncDecision {
    if (parsed.episodes.isEmpty() && snapshot.knownUpstream.isEmpty()) {
        return FeedSyncDecision.NoChange
    }

    val knownByGuid = snapshot.knownUpstream.associateBy { it.upstreamGuid }
    val mappingByGuid = snapshot.mappings.associateBy { it.upstreamGuid }
    val guidByHostedId =
        snapshot.mappings
            .mapNotNull { m -> m.hostedEpisodeId?.let { it to m.upstreamGuid } }
            .toMap()
    val parsedGuids = parsed.episodes.map { it.upstreamGuid }.toSet()

    val upserts =
        parsed.episodes.map { ep ->
            val existing = knownByGuid[ep.upstreamGuid]
            UpstreamEpisodeRecord(
                podcastId = snapshot.podcastId,
                upstreamGuid = ep.upstreamGuid,
                title = ep.title,
                season = ep.season,
                episode = ep.episode,
                publishedAt = ep.publishedAt,
                upstreamAudioUrl = ep.upstreamAudioUrl,
                upstreamAudioSize = ep.upstreamAudioSize,
                upstreamAudioMime = ep.upstreamAudioMime,
                durationSeconds = ep.durationSeconds,
                firstSeenAt = existing?.firstSeenAt ?: now,
                lastSeenAt = now,
            )
        }

    val removedGuids = snapshot.knownUpstream.map { it.upstreamGuid }.filter { it !in parsedGuids }

    val newMappings =
        upserts
            .filter { it.upstreamGuid !in mappingByGuid }
            .mapNotNull { upstream ->
                val match = attemptAutoMatch(upstream, snapshot.hostedEpisodes, guidByHostedId)
                when (match) {
                    is MatchResult.Matched ->
                        EpisodeMapping(
                            podcastId = snapshot.podcastId,
                            upstreamGuid = upstream.upstreamGuid,
                            hostedEpisodeId = match.hostedEpisodeId,
                            mode = match.mode,
                            manualOverride = false,
                            updatedAt = now,
                        )

                    MatchResult.NoMatch ->
                        EpisodeMapping(
                            podcastId = snapshot.podcastId,
                            upstreamGuid = upstream.upstreamGuid,
                            hostedEpisodeId = null,
                            mode = EpisodeMappingMode.UNMATCHED,
                            manualOverride = false,
                            updatedAt = now,
                        )
                }
            }

    return FeedSyncDecision.CacheUpdated(
        rawXml = parsed.rawXml,
        channelTitle = parsed.channelTitle,
        byteSize = parsed.rawXml.toByteArray().size.toLong(),
        fetchedAt = now,
        upserts = upserts,
        removedGuids = removedGuids,
        newMappings = newMappings,
    )
}

/**
 * Match an upstream episode to a hosted episode. Season+episode tuple match wins; falls back to
 * NoMatch (manual mapping UI handles the rest). Hosted episodes already claimed by another upstream
 * guid are excluded.
 */
fun attemptAutoMatch(
    upstream: UpstreamEpisodeRecord,
    hosted: List<EpisodeEntry>,
    claimedHostedToGuid: Map<PodcastEpisodeId, UpstreamGuid>,
): MatchResult {
    val season = upstream.season
    val episode = upstream.episode
    if (season == null || episode == null) return MatchResult.NoMatch

    val candidates =
        hosted.filter {
            it.season == season &&
                it.episode == episode &&
                claimedHostedToGuid[it.id].let { claimed ->
                    claimed == null || claimed == upstream.upstreamGuid
                }
        }
    return when (candidates.size) {
        1 -> MatchResult.Matched(candidates.single().id, EpisodeMappingMode.AUTO_SEASON_EPISODE)
        else -> MatchResult.NoMatch
    }
}

/**
 * Validate a manual mapping request: prevent reassigning a hosted episode already mapped to a
 * different upstream guid (unless caller is clearing).
 */
fun decideManualMapping(
    snapshot: FeedSyncSnapshot,
    upstreamGuid: UpstreamGuid,
    hostedEpisodeId: PodcastEpisodeId?,
    now: Instant,
): MappingDecision {
    val existing = snapshot.mappings.firstOrNull { it.upstreamGuid == upstreamGuid }

    if (hostedEpisodeId == null) {
        if (existing?.hostedEpisodeId == null) return MappingDecision.NoChange
        return MappingDecision.Cleared(snapshot.podcastId, upstreamGuid)
    }

    val conflict =
        snapshot.mappings.firstOrNull {
            it.hostedEpisodeId == hostedEpisodeId && it.upstreamGuid != upstreamGuid
        }
    if (conflict != null) return MappingDecision.Conflict(conflict.upstreamGuid)

    if (
        existing?.hostedEpisodeId == hostedEpisodeId &&
            existing.mode == EpisodeMappingMode.MANUAL &&
            existing.manualOverride
    ) {
        return MappingDecision.NoChange
    }

    return MappingDecision.ManualSet(
        EpisodeMapping(
            podcastId = snapshot.podcastId,
            upstreamGuid = upstreamGuid,
            hostedEpisodeId = hostedEpisodeId,
            mode = EpisodeMappingMode.MANUAL,
            manualOverride = true,
            updatedAt = now,
        )
    )
}
