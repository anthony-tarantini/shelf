# Podcast & Ad-Free Ingestion Engine — Detailed Implementation Plan

This document is the implementation blueprint for the podcast ingestion engine. It maps each feature to concrete domain types, schemas, services, and routes following Shelf's established DDD/FP patterns.

**Architectural Invariant:** Strict adherence to Identity pattern, Arrow Raise context, value classes, decider-driven mutations, and domain event handling — exactly as `catalog/book` and `catalog/author` demonstrate.

---

## Phase 0: Domain Foundation

### 0.1 New Domain Module: `catalog/podcast`

```
catalog/podcast/
  domain/
    primitives.kt    — PodcastId, FeedUrl, Season, EpisodeNumber, FeedToken
    models.kt        — PodcastRoot<S>, PodcastAggregate<S>, PodcastSummary, EpisodeEntry
    commands.kt      — CreatePodcastCommand, UpdatePodcastCommand, SyncFeedCommand
    decider.kt       — PodcastDecider (pure decision functions)
    error.kt         — PodcastError sealed hierarchy
    events.kt        — PodcastDomainEvent sealed interface
    api.kt           — PodcastSnapshot, PodcastMutation, PodcastDecision
  persistence.kt     — SQLDelight adapter extensions
  readRepository.kt  — PodcastReadRepository interface + impl
  repository.kt      — PodcastMutationRepository interface + impl
  service.kt         — PodcastService composing providers/modifiers
  routes.kt          — Thin HTTP boundary
```

### 0.2 New Domain Module: `processing/sanitization` — Milestone 2

```
processing/sanitization/
  domain/
    primitives.kt    — SanitizationJobId, AdSegment, AudioTimestamp
    models.kt        — SanitizationJob<S>, SanitizationResult, DetectedSegment
    commands.kt      — StartSanitizationCommand, ApproveSanitizationCommand, RejectSanitizationCommand
    decider.kt       — SanitizationDecider (state machine transitions)
    error.kt         — SanitizationError sealed hierarchy
    events.kt        — SanitizationDomainEvent
    api.kt           — SanitizationSnapshot, SanitizationMutation
  persistence.kt
  repository.kt
  service.kt
```

### 0.3 New Integration Module: `integration/podcast`

```
integration/podcast/
  feed/
    FeedParser.kt         — RSS/Atom XML → domain types
    FeedFetchAdapter.kt   — HTTP client for fetching feeds
  metadata/
    ApplePodcastsAdapter.kt  — Apple Podcasts Search API
    PodcastIndexAdapter.kt   — Podcast Index API (open, free)
  audio/
    FfmpegAdapter.kt     — FFmpeg CLI wrapper (silence detect, concat, transcode)
    WhisperAdapter.kt    — whisper.cpp CLI wrapper for transcription
    LlmAdapter.kt        — Local LLM HTTP client for ad classification
```

---

## Phase 1: Data Model & Persistence

### 1.1 Domain Primitives (`catalog/podcast/domain/primitives.kt`)

```kotlin
@JvmInline @Serializable
value class PodcastId private constructor(override val value: Uuid) : UuidValueClass {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(id: String?): PodcastId { /* validate */ }
        fun fromRaw(value: Uuid) = PodcastId(value)
        fun fromRaw(value: String) = PodcastId(Uuid.parse(value))
        val adapter = object : UuidAdapter<PodcastId>(::fromRaw) {}
    }
}

@JvmInline
value class FeedUrl private constructor(val value: String) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: String): FeedUrl {
            val normalized = raw.trim()
            ensure(normalized.isNotEmpty()) { EmptyFeedUrl }
            val uri = catch({ URI(normalized) }) { raise(InvalidFeedUrl) }
            ensure(uri.scheme?.lowercase() in listOf("https", "http")) { InvalidFeedUrl }
            ensure(!uri.host.isNullOrBlank()) { InvalidFeedUrl }
            return FeedUrl(normalized)
        }
        fun fromRaw(raw: String) = FeedUrl(raw)
    }
}

@JvmInline
value class FeedToken private constructor(val value: String) {
    companion object {
        /** Validate untrusted input (route parameters, user input). */
        context(_: RaiseContext)
        operator fun invoke(raw: String?): FeedToken {
            ensureNotNull(raw) { InvalidFeedToken }
            ensure(raw.isNotBlank()) { InvalidFeedToken }
            // Token format: UUID string — validate structure to reject garbage early
            ensure(Uuid.parseOrNull(raw) != null) { InvalidFeedToken }
            return FeedToken(raw)
        }
        fun generate(): FeedToken = FeedToken(Uuid.random().toString())
        /** Trusted DB rehydration only. */
        fun fromRaw(raw: String) = FeedToken(raw)
    }
}

/** Podcast episode ordering — two discrete integers, no floating-point ambiguity. */
@JvmInline
value class Season private constructor(val value: Int) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: Int?): Season {
            val s = raw ?: 0
            ensure(s in 0..999) { InvalidEpisodeIndex }
            return Season(s)
        }
        fun fromRaw(value: Int) = Season(value)
    }
}

@JvmInline
value class EpisodeNumber private constructor(val value: Int) {
    companion object {
        context(_: RaiseContext)
        operator fun invoke(raw: Int): EpisodeNumber {
            ensure(raw in 0..99999) { InvalidEpisodeIndex }
            return EpisodeNumber(raw)
        }
        fun fromRaw(value: Int) = EpisodeNumber(value)
    }
}
```

### 1.2 Domain Models (`catalog/podcast/domain/models.kt`)

```kotlin
@Serializable
@ConsistentCopyVisibility
data class PodcastRoot<S : PersistenceState> private constructor(
    val id: Identity<S, PodcastId>,
    val seriesId: SeriesId,              // FK to existing series table
    val feedUrl: FeedUrl,
    val feedToken: FeedToken,            // For private RSS distribution
    val feedTokenExpiresAt: Instant?,    // NULL = no expiry
    val feedTokenPrevious: FeedToken?,   // Grace period during rotation
    val feedTokenPreviousExpiresAt: Instant?,
    val autoSanitize: Boolean,
    val autoFetch: Boolean,
    val lastFetchedAt: Instant?,
    val fetchIntervalMinutes: Int,       // Default 60
    val version: Int,                    // Bumped on episode changes, used for ETag
) {
    /** Check if a token is currently valid (current or grace-period previous). */
    fun isTokenValid(token: FeedToken, now: Instant): Boolean =
        (token == feedToken && (feedTokenExpiresAt == null || now < feedTokenExpiresAt)) ||
        (token == feedTokenPrevious && feedTokenPreviousExpiresAt != null && now < feedTokenPreviousExpiresAt)

    companion object {
        fun new(seriesId: SeriesId, feedUrl: FeedUrl, feedToken: FeedToken, 
                autoSanitize: Boolean = true, autoFetch: Boolean = true,
                fetchIntervalMinutes: Int = 60) =
            PodcastRoot<PersistenceState.Unsaved>(
                Identity.Unsaved, seriesId, feedUrl, feedToken,
                feedTokenExpiresAt = null, feedTokenPrevious = null,
                feedTokenPreviousExpiresAt = null,
                autoSanitize, autoFetch, null, fetchIntervalMinutes, version = 0
            )

        fun fromRaw(id: PodcastId, seriesId: SeriesId, feedUrl: FeedUrl,
                    feedToken: FeedToken, feedTokenExpiresAt: Instant?,
                    feedTokenPrevious: FeedToken?, feedTokenPreviousExpiresAt: Instant?,
                    autoSanitize: Boolean, autoFetch: Boolean,
                    lastFetchedAt: Instant?, fetchIntervalMinutes: Int, version: Int) =
            PodcastRoot<PersistenceState.Persisted>(
                Identity.Persisted(id), seriesId, feedUrl, feedToken,
                feedTokenExpiresAt, feedTokenPrevious, feedTokenPreviousExpiresAt,
                autoSanitize, autoFetch, lastFetchedAt, fetchIntervalMinutes, version
            )
    }
}

typealias SavedPodcastRoot = PodcastRoot<PersistenceState.Persisted>
typealias NewPodcastRoot = PodcastRoot<PersistenceState.Unsaved>

@Serializable
data class PodcastAggregate<S : PersistenceState>(
    val podcast: PodcastRoot<S>,
    val seriesId: SeriesId,              // Cross-root association by ID only
    val seriesTitle: String,             // Denormalized for display
    val episodes: List<EpisodeEntry>,
    val credential: CredentialStatus,    // HAS_CREDENTIAL | NO_CREDENTIAL (never expose value)
)

@Serializable
data class PodcastSummary(
    val id: PodcastId,
    val seriesTitle: String,
    val feedUrl: String,
    val episodeCount: Long,
    val autoSanitize: Boolean,
    val autoFetch: Boolean,
    val lastFetchedAt: Instant?,
    val coverPath: StoragePath?,
)

/** Episode = Book with AUDIOBOOK edition, linked via episode_ordering (NOT series_books) */
@Serializable
data class EpisodeEntry(
    val bookId: BookId,
    val title: String,
    val season: Int,                     // From episode_ordering.season
    val episode: Int,                    // From episode_ordering.episode
    val sanitizationStatus: SanitizationStatus?, // NULL until Milestone 2 (sanitization)
    val coverPath: StoragePath?,
    val totalTime: Double?,
    val publishedAt: Instant?,
)

/** Introduced in Milestone 2. NULL on EpisodeEntry in Milestone 1. */
enum class SanitizationStatus {
    PENDING,        // Queued, not yet processed
    PROCESSING,     // Active sanitization in progress
    REVIEW,         // Done, awaiting user approval
    APPROVED,       // User confirmed sanitized version
    REJECTED,       // User rejected, reverted to original
    FAILED,         // Pipeline error (ffmpeg, whisper, etc.) — retryable from PENDING
    SKIPPED,        // auto_sanitize=false or premium feed
}

enum class CredentialStatus { HAS_CREDENTIAL, NO_CREDENTIAL }
```

### 1.3 Error Types (`catalog/podcast/domain/error.kt`)

```kotlin
sealed interface PodcastError : AppError

// Persistence errors
sealed interface PodcastPersistenceError : PodcastError
object PodcastNotFound : PodcastPersistenceError
object PodcastAlreadyExists : PodcastPersistenceError
object PodcastFeedAlreadySubscribed : PodcastPersistenceError

// Validation errors
sealed interface PodcastValidationError : PodcastError
object EmptyFeedUrl : PodcastValidationError
object InvalidFeedUrl : PodcastValidationError
object InvalidPodcastId : PodcastValidationError
object InvalidFeedToken : PodcastValidationError
object InvalidEpisodeIndex : PodcastValidationError

// Integration errors
sealed interface PodcastIntegrationError : PodcastError
object FeedFetchFailed : PodcastIntegrationError
object FeedParseFailed : PodcastIntegrationError
object FeedAuthRequired : PodcastIntegrationError
data class FeedRateLimited(val retryAfterSeconds: Int?) : PodcastIntegrationError

// Sanitization errors
sealed interface SanitizationError : AppError
object SanitizationJobNotFound : SanitizationError
object InvalidSanitizationTransition : SanitizationError
object FfmpegExecutionFailed : SanitizationError
object TranscriptionFailed : SanitizationError
object OriginalFileNotFound : SanitizationError
```

### 1.4 SQLDelight Schema (`catalog/podcast/persistence/podcast.sq`)

```sql
import io.tarantini.shelf.catalog.podcast.domain.PodcastId;
import io.tarantini.shelf.catalog.podcast.domain.FeedToken;
import io.tarantini.shelf.catalog.series.domain.SeriesId;

CREATE TABLE IF NOT EXISTS podcasts (
    id UUID AS PodcastId PRIMARY KEY DEFAULT gen_random_uuid(),
    series_id UUID AS SeriesId NOT NULL UNIQUE REFERENCES series(id) ON DELETE CASCADE,
    feed_url TEXT NOT NULL,
    feed_token TEXT AS FeedToken NOT NULL UNIQUE,
    feed_token_expires_at TIMESTAMP WITH TIME ZONE,
    feed_token_previous TEXT,
    feed_token_previous_expires_at TIMESTAMP WITH TIME ZONE,
    auto_sanitize BOOLEAN NOT NULL DEFAULT TRUE,
    auto_fetch BOOLEAN NOT NULL DEFAULT TRUE,
    last_fetched_at TIMESTAMP WITH TIME ZONE,
    fetch_interval_minutes INTEGER NOT NULL DEFAULT 60,
    version INTEGER NOT NULL DEFAULT 0,
    sys_period tstzrange NOT NULL DEFAULT tstzrange(current_timestamp, NULL)
);

CREATE INDEX IF NOT EXISTS idx_podcasts_series ON podcasts(series_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_podcasts_feed_token ON podcasts(feed_token);

-- Summary view joining series data and episode counts.
-- Uses episode_ordering, NOT series_books. Podcast episodes are never inserted into series_books.
CREATE OR REPLACE VIEW podcastSummaries AS
SELECT
    p.id,
    s.title AS series_title,
    p.feed_url,
    p.auto_sanitize,
    p.auto_fetch,
    p.last_fetched_at,
    count(DISTINCT eo.book_id) AS episode_count,
    (SELECT b.cover_path FROM books b
     JOIN episode_ordering eo2 ON b.id = eo2.book_id
     WHERE eo2.podcast_id = p.id AND b.cover_path IS NOT NULL
     ORDER BY eo2.season ASC, eo2.episode ASC LIMIT 1) AS cover_path
FROM podcasts p
JOIN series s ON p.series_id = s.id
LEFT JOIN episode_ordering eo ON eo.podcast_id = p.id
GROUP BY p.id, s.id;

-- Queries
selectAll:
SELECT * FROM podcastSummaries ORDER BY series_title ASC;

selectById:
SELECT * FROM podcasts WHERE id = :id;

selectBySeriesId:
SELECT * FROM podcasts WHERE series_id = :seriesId;

selectByFeedToken:
SELECT * FROM podcasts WHERE feed_token = :token;

selectSummaryById:
SELECT * FROM podcastSummaries WHERE id = :id;

-- Mutations
insert:
INSERT INTO podcasts (series_id, feed_url, feed_token, auto_sanitize, auto_fetch, fetch_interval_minutes)
VALUES (:seriesId, :feedUrl, :feedToken, :autoSanitize, :autoFetch, :fetchIntervalMinutes)
RETURNING id;

updateFeedConfig:
UPDATE podcasts
SET auto_sanitize = :autoSanitize,
    auto_fetch = :autoFetch,
    fetch_interval_minutes = :fetchIntervalMinutes
WHERE id = :id;

updateLastFetched:
UPDATE podcasts SET last_fetched_at = :fetchedAt WHERE id = :id;

rotateToken:
UPDATE podcasts
SET feed_token_previous = feed_token,
    feed_token_previous_expires_at = :graceExpiresAt,
    feed_token = :newToken
WHERE id = :id;

revokeToken:
UPDATE podcasts
SET feed_token = :newToken,
    feed_token_previous = NULL,
    feed_token_previous_expires_at = NULL
WHERE id = :id;

bumpVersion:
UPDATE podcasts SET version = version + 1 WHERE id = :id;

selectByFeedTokenIncludingPrevious:
SELECT * FROM podcasts
WHERE (feed_token = :token
       AND (feed_token_expires_at IS NULL OR feed_token_expires_at > CURRENT_TIMESTAMP))
   OR (feed_token_previous = :token
       AND feed_token_previous_expires_at IS NOT NULL
       AND feed_token_previous_expires_at > CURRENT_TIMESTAMP);

deleteById:
DELETE FROM podcasts WHERE id = :id;
```

### 1.5 Sanitization Job Schema — Milestone 2 (`processing/sanitization/persistence/sanitization.sq`)

```sql
import io.tarantini.shelf.processing.sanitization.domain.SanitizationJobId;
import io.tarantini.shelf.catalog.metadata.domain.EditionId;
import io.tarantini.shelf.catalog.book.domain.BookId;

CREATE TABLE IF NOT EXISTS sanitization_jobs (
    id UUID AS SanitizationJobId PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id UUID AS BookId NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    edition_id UUID AS EditionId NOT NULL REFERENCES editions(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'PENDING',
    original_path TEXT NOT NULL,
    sanitized_path TEXT,
    transcript_path TEXT,
    detected_segments JSONB,          -- Array of {start, end, confidence, label}
    total_removed_seconds DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT
);

-- Partial unique: only one PENDING/PROCESSING/REVIEW job per edition at a time.
-- Completed (APPROVED/REJECTED/FAILED) rows kept for history/retry.
CREATE UNIQUE INDEX IF NOT EXISTS idx_sanitization_active_edition
ON sanitization_jobs(edition_id) WHERE status IN ('PENDING', 'PROCESSING', 'REVIEW');

CREATE INDEX IF NOT EXISTS idx_sanitization_status ON sanitization_jobs(status);
CREATE INDEX IF NOT EXISTS idx_sanitization_book ON sanitization_jobs(book_id);

selectByBookId:
SELECT * FROM sanitization_jobs WHERE book_id = :bookId;

selectByEditionId:
SELECT * FROM sanitization_jobs WHERE edition_id = :editionId;

selectByStatus:
SELECT * FROM sanitization_jobs WHERE status = :status ORDER BY created_at ASC;

selectPending:
SELECT * FROM sanitization_jobs WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit;

insert:
INSERT INTO sanitization_jobs (book_id, edition_id, original_path)
VALUES (:bookId, :editionId, :originalPath)
RETURNING id;

updateStatus:
UPDATE sanitization_jobs
SET status = :status, updated_at = CURRENT_TIMESTAMP, error_message = :errorMessage
WHERE id = :id;

updateResult:
UPDATE sanitization_jobs
SET status = 'REVIEW',
    sanitized_path = :sanitizedPath,
    transcript_path = :transcriptPath,
    detected_segments = :detectedSegments,
    total_removed_seconds = :totalRemovedSeconds,
    updated_at = CURRENT_TIMESTAMP
WHERE id = :id;

deleteByBookId:
DELETE FROM sanitization_jobs WHERE book_id = :bookId;
```

### 1.6 Integration Credentials Schema (`integration/persistence/credentials.sq`)

```sql
import io.tarantini.shelf.catalog.podcast.domain.PodcastId;

CREATE TABLE IF NOT EXISTS integration_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    podcast_id UUID NOT NULL UNIQUE REFERENCES podcasts(id) ON DELETE CASCADE,
    credential_type TEXT NOT NULL,     -- 'HTTP_BASIC', 'BEARER', 'COOKIE', 'ACTIVATION_BYTES'
    encrypted_value BYTEA NOT NULL,    -- AES-256-GCM encrypted
    iv BYTEA NOT NULL,                 -- Initialization vector (unique per row)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

selectByPodcastId:
SELECT * FROM integration_credentials WHERE podcast_id = :podcastId;

upsert:
INSERT INTO integration_credentials (podcast_id, credential_type, encrypted_value, iv)
VALUES (:podcastId, :credentialType, :encryptedValue, :iv)
ON CONFLICT (podcast_id)
DO UPDATE SET
    credential_type = EXCLUDED.credential_type,
    encrypted_value = EXCLUDED.encrypted_value,
    iv = EXCLUDED.iv;

deleteByPodcastId:
DELETE FROM integration_credentials WHERE podcast_id = :podcastId;

existsByPodcastId:
SELECT count(*) > 0 FROM integration_credentials WHERE podcast_id = :podcastId;
```

---

## Phase 2: Sanitization Pipeline (MinusPod Orchestration) — Milestone 2

### 2.1 Overview
Instead of building a custom ad-detection engine, Shelf uses **MinusPod** as a sidecar container. Shelf acts as the master catalog and feed manager, while MinusPod provides the ad-removal "crushing" service via its REST API.

### 2.2 Integration Flow
1. **Infrastructure:** MinusPod runs as a sidecar (`minuspod` service in Docker).
2. **Registration:** When a podcast with `autoSanitize=true` is added to Shelf, Shelf registers the feed with MinusPod.
3. **Proxying:** Shelf uses MinusPod's "modified" RSS feed or direct audio proxy URLs for episode ingestion.
4. **Polling:** Shelf polls MinusPod's API to track processing status (Transcribing, Detecting, Splicing).

### 2.3 MinusPod Adapter (`integration/podcast/sanitization/MinusPodAdapter.kt`)
A REST client for MinusPod's `/api/v1/` endpoints:
- `POST /feeds` — Register a new feed.
- `GET /feeds/{slug}/episodes` — Track processing status of episodes.
- `POST /feeds/{slug}/reprocess-all` — Trigger a manual re-crush.


### 2.3 Chapter Timestamp Adjustment

Critical correctness requirement. Algorithm:

```kotlin
fun adjustChapters(
    chapters: List<Chapter>,
    removedSegments: List<AdSegment>,  // sorted by start ascending
): List<Chapter> {
    val sorted = removedSegments.sortedBy { it.start }
    return chapters.map { chapter ->
        var offset = 0.0
        for (segment in sorted) {
            if (segment.end <= chapter.startTime) {
                // Entire removed segment before this chapter
                offset += segment.duration
            } else if (segment.start < chapter.startTime) {
                // Partial overlap at chapter start
                offset += chapter.startTime - segment.start
            }
            // Segments after chapter start don't affect start time
        }
        chapter.copy(
            startTime = chapter.startTime - offset,
            endTime = chapter.endTime?.let { end ->
                var endOffset = 0.0
                for (segment in sorted) {
                    if (segment.end <= end) endOffset += segment.duration
                    else if (segment.start < end) endOffset += end - segment.start
                }
                end - endOffset
            }
        )
    }
}
```

### 2.4 Job Queue Extension

Extend existing `JobQueue` interface:

```kotlin
interface JobQueue {
    suspend fun enqueueSyncMetadataJob(bookId: BookId)
    suspend fun enqueueSanitizationJob(jobId: SanitizationJobId)
    suspend fun enqueueFeedFetchJob(podcastId: PodcastId)
}
```

New workers in `processing/jobs/`:
- `SanitizationWorker` — polls `jobs:sanitization` queue, runs pipeline stages
- `FeedFetchWorker` — polls `jobs:feed_fetch` queue, fetches + parses feeds

Both follow existing `SyncMetadataWorker` pattern (Valkey + in-memory channel).

---

## Phase 3: Feed Fetching & Episode Ingestion

### 3.1 Feed Parser (`integration/podcast/feed/FeedParser.kt`)

```kotlin
data class ParsedFeed(
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,                   // Unique episode identifier from feed
    val title: String,
    val description: String?,
    val audioUrl: String,
    val duration: Duration?,
    val publishedAt: Instant?,
    val season: Int?,
    val episode: Int?,
    val imageUrl: String?,
)

interface FeedParser {
    context(_: RaiseContext)
    suspend fun parse(xml: String): ParsedFeed
}
```

Uses `javax.xml.parsers` (already on JVM) — no extra dependency. Handles RSS 2.0 + iTunes namespace + Podcast 2.0 namespace.

**XXE Hardening (mandatory):** Feed XML is untrusted input. Parser factory MUST disable external entities:

```kotlin
private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        // Prevent XXE attacks — feeds are untrusted external input
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
```

### 3.2 Episode Ingestion Flow

```
FeedFetchWorker
  → FeedFetchAdapter.fetch(feedUrl, credentials?)
  → FeedParser.parse(xml)
  → For each episode in feed:
      1. Check if GUID already exists (SELECT) — skip if found
      2. Download audio file to storage
      3. Within single DB transaction:
         a. Create Book (title = episode title)
         b. Claim GUID with new book_id (INSERT ... ON CONFLICT DO NOTHING RETURNING)
            — If NULL returned, another worker raced us. Rollback transaction, skip.
         c. Insert episode_ordering row (season, episode)
         d. Create edition with AUDIOBOOK format
      4. Extract chapters from m4b if present
      5. Update podcast.last_fetched_at + bump version
      6. [Milestone 2] If auto_sanitize=true: enqueue sanitization job
```

**Idempotency & Concurrency:** Two workers must not create duplicate episodes for same GUID.

Step 1 (pre-check) is a fast path — avoids downloading audio for known episodes. But it's not sufficient alone because of TOCTOU races between workers.

Step 3b is the authoritative guard. Book row exists (step 3a) so FK is satisfied. If another worker already claimed this GUID between step 1 and step 3b, `ON CONFLICT DO NOTHING RETURNING` returns NULL. Transaction rolls back, orphaned book row is cleaned up.

```kotlin
// Inside transaction — book already created at this point, FK satisfied.
// INSERT ... ON CONFLICT DO NOTHING RETURNING guarantees atomic claim.
// If no row returned, another worker raced us. Rollback + clean up downloaded file.
val claimed = podcastQueries.claimEpisodeGuid(podcastId, episode.guid, bookId)
if (claimed == null) {
    rollback()          // Cleans up book row from step 3a
    deleteFile(audioPath) // Clean up downloaded file from step 2
    continue
}
```

**Orphan file cleanup:** Audio download (step 2) happens before the transaction because downloads are slow and shouldn't hold a DB transaction open. If the GUID claim loses the race in step 3b, the downloaded file is an orphan. The `deleteFile(audioPath)` call immediately after rollback handles this. If the process crashes between download and cleanup, a periodic storage reconciliation job (compare files on disk vs edition paths in DB) catches stragglers — same pattern used for failed book imports today.

Track seen GUIDs via `episode_guids` table:

```sql
CREATE TABLE IF NOT EXISTS episode_guids (
    podcast_id UUID NOT NULL REFERENCES podcasts(id) ON DELETE CASCADE,
    guid TEXT NOT NULL,
    book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    PRIMARY KEY(podcast_id, guid)
);

claimEpisodeGuid:
INSERT INTO episode_guids (podcast_id, guid, book_id)
VALUES (:podcastId, :guid, :bookId)
ON CONFLICT (podcast_id, guid) DO NOTHING
RETURNING guid;
```

Podcast-specific episode ordering via dedicated table (avoids overloading `series_books.index` with float encoding):

```sql
CREATE TABLE IF NOT EXISTS episode_ordering (
    podcast_id UUID NOT NULL REFERENCES podcasts(id) ON DELETE CASCADE,
    book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    season INTEGER NOT NULL DEFAULT 0,
    episode INTEGER NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY(podcast_id, book_id),
    UNIQUE(podcast_id, season, episode)   -- No two episodes share same S/E number
);

CREATE INDEX IF NOT EXISTS idx_episode_ordering_podcast
ON episode_ordering(podcast_id, season ASC, episode ASC);

selectMaxEpisodeForSeason:
SELECT COALESCE(MAX(episode), 0) FROM episode_ordering
WHERE podcast_id = :podcastId AND season = :season;
```

Sort order: `ORDER BY season ASC, episode ASC`. No floating-point ambiguity.

**Separation from `series_books`:** Podcast episodes are NEVER inserted into `series_books`. The two relationship tables serve different domains:
- `series_books` — books in a series (novels, trilogies). Uses `index` float for ordering.
- `episode_ordering` — podcast episodes. Uses integer `(season, episode)` pairs. Links to Series indirectly through `episode_ordering.podcast_id → podcasts.series_id`.

This prevents: mixed book/episode counts in `seriesSummaries` view, format-filter hacks on shared queries, accidental cascade when deleting a podcast subscription, and ordering model conflicts (float vs integer).

**Episode number derivation policy:** Many real feeds omit `<itunes:episode>` or reuse numbers across seasons. Derivation rules:

1. **Both `season` and `episode` present in feed:** Use as-is. If `UNIQUE(podcast_id, season, episode)` conflicts, append suffix: try `episode + 1`, `episode + 2`, etc.
2. **`episode` present, `season` missing:** `season = 0` (seasonless). Episode number used directly.
3. **`episode` missing, `season` present:** Auto-assign `episode = MAX(episode) + 1` for that season (via `selectMaxEpisodeForSeason`).
4. **Both missing (common for older feeds):** `season = 0`, `episode = MAX(episode) + 1` for season 0. Effectively sequential by ingestion order.
5. **Feed provides `published_at`:** Store in `episode_ordering.published_at`. UI can offer "sort by publish date" as alternative to season/episode order.

This ensures every episode gets a unique `(season, episode)` pair regardless of feed quality.

Prevents duplicate ingestion on re-fetch. PRIMARY KEY enforces uniqueness at DB level regardless of application-level races.

### 3.3 Fetch Scheduling

`FeedFetchScheduler` runs on application startup:

```kotlin
class FeedFetchScheduler(
    private val scope: CoroutineScope,
    private val podcastQueries: PodcastQueries,
    private val jobQueue: JobQueue,
) {
    fun start() {
        scope.launch {
            while (isActive) {
                val due = podcastQueries.selectDuePodcasts().executeAsList()
                due.forEach { podcast ->
                    if (podcast.autoFetch) {
                        jobQueue.enqueueFeedFetchJob(PodcastId.fromRaw(podcast.id))
                    }
                }
                delay(60.seconds)  // Check every minute
            }
        }
    }
}
```

Add query to podcast.sq:

```sql
selectDuePodcasts:
SELECT * FROM podcasts
WHERE auto_fetch = TRUE
  AND (last_fetched_at IS NULL
       OR last_fetched_at + (fetch_interval_minutes || ' minutes')::interval < CURRENT_TIMESTAMP);
```

Note: Query uses `CURRENT_TIMESTAMP` — no parameter needed. Kotlin call site is parameterless:

```kotlin
val due = podcastQueries.selectDuePodcasts().executeAsList()
```

---

## Phase 4: Private RSS Distribution

### 4.1 RSS Route (`catalog/podcast/rss/routes.kt`)

```kotlin
fun Route.podcastRssRoutes(rssService: PodcastRssService) {
    // No JWT — token-authenticated via URL path
    get("/api/rss/podcasts/{token}") {
        respond({
            // FeedToken() validates untrusted route input (not fromRaw)
            val token = FeedToken(call.parameters["token"])
            rssService.generateFeed(token, call.request)
        })
    }

    // Byte-range audio streaming
    get("/api/rss/podcasts/{token}/episodes/{bookId}/audio") {
        either {
            // Both use validated constructors — untrusted route input
            val token = FeedToken(call.parameters["token"])
            val bookId = BookId(call.parameters["bookId"])
            rssService.resolveAudioFile(token, bookId)
        }.fold(
            { err -> call.respond(err.toHttpResponse()) },
            { path -> call.respondFile(path.toFile()) }  // Ktor handles Range/206 for respondFile
        )
    }
}
```

**Storage-root confinement:** `rssService.resolveAudioFile()` MUST resolve paths through `StorageService.resolve()`, which validates the resolved path stays within the configured storage root. The service returns a confined `Path` — the route never constructs filesystem paths directly. This matches the trust-boundary rules in AGENTS.md.

### 4.2 RSS XML Generation (`catalog/podcast/rss/RssService.kt`)

Service layer — not in route. Generates Podcast 2.0 compliant XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
     xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
     xmlns:podcast="https://podcastindex.org/namespace/1.0">
  <channel>
    <title>{series.title}</title>
    <description>{series.description}</description>
    <itunes:image href="{coverUrl}"/>
    <podcast:locked>yes</podcast:locked>
    {for each episode:}
    <item>
      <title>{book.title}</title>
      <enclosure url="{audioStreamUrl}" length="{size}" type="audio/x-m4b"/>
      <itunes:duration>{totalTime}</itunes:duration>
      <podcast:transcript url="{transcriptUrl}" type="application/srt"/>
      <podcast:chapters url="{chaptersJsonUrl}" type="application/json+chapters"/>
    </item>
  </channel>
</rss>
```

Key details:
- `<enclosure url>` points to token-authenticated audio endpoint
- `<podcast:transcript>` included when whisper transcript exists (free byproduct of sanitization)
- `<podcast:chapters>` as JSON Chapters format (Podcast 2.0 spec)
- Response includes `ETag` based on `podcasts.version` counter (see caching below)
- `Cache-Control: private, max-age=300` — 5 min cache, private (token-auth)

### 4.4 Feed Token Lifecycle

Token-in-URL is unavoidable — podcast players don't support auth headers. Mitigations:

**Schema addition to `podcasts` table:**
```sql
    feed_token_expires_at TIMESTAMP WITH TIME ZONE,        -- NULL = no expiry
    feed_token_previous TEXT,                               -- Grace period for rotation
    feed_token_previous_expires_at TIMESTAMP WITH TIME ZONE -- When old token stops working
```

**Rotation:** User generates new token via UI. Old token enters 7-day grace period (configurable). Both tokens valid during grace window. After grace expires, old token rejected.

**Revocation:** Immediate — set `feed_token` to new random value, clear `feed_token_previous`. All existing player sessions break instantly. UI warns user.

**Expiry (optional):** `feed_token_expires_at` checked on every RSS/audio request. Expired token returns 401. User must generate new token.

**Log redaction:** Feed tokens MUST be redacted in access logs. Ktor access log config should mask the `{token}` path segment. Token values never appear in application logs, metrics, or span attributes (per AGENTS.md telemetry hygiene mandate #12).

### 4.5 ETag / Caching Strategy

Maintain a `version` counter on `podcasts` table:

```sql
ALTER TABLE podcasts ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
```

Bump on any episode add/remove/sanitization complete:

```sql
bumpVersion:
UPDATE podcasts SET version = version + 1 WHERE id = :id;
```

ETag = `"{podcastId}-{version}"`. Deterministic, cheap to compute, no timestamp fragility.

### 4.3 Byte-Range Streaming

Ktor's `respondFile` handles `Range` headers and `206 Partial Content` natively. No custom implementation needed. Verify:
- `Accept-Ranges: bytes` header present
- `Content-Range` header correct on 206
- Pocket Casts, Overcast, Apple Podcasts compatibility

---

## Phase 5: Security & Credential Management

### 5.1 Encryption Service (`integration/security/EncryptionService.kt`)

```kotlin
class EncryptionService(encryptionSecret: String) {
    private val secretKey: SecretKey = deriveKey(encryptionSecret)

    fun encrypt(plaintext: ByteArray): EncryptedPayload {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return EncryptedPayload(ciphertext = cipher.doFinal(plaintext), iv = iv)
    }

    fun decrypt(payload: EncryptedPayload): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, payload.iv))
        return cipher.doFinal(payload.ciphertext)
    }

    private fun deriveKey(secret: String): SecretKey {
        // PBKDF2 derivation from secret → stable AES key
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(secret.toCharArray(), "shelf-integration".toByteArray(), 100_000, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}

data class EncryptedPayload(val ciphertext: ByteArray, val iv: ByteArray)
```

Key decisions:
- Per-row unique IV (stored alongside ciphertext) — prevents IV reuse
- `ENCRYPTION_SECRET` env var, separate from `JWT_SECRET`
- Key rotation: re-encrypt all rows when secret changes (migration task, not automated)
- Encryption/decryption lives ONLY in `integration` layer — catalog never touches it
- Log sanitization via Logback filter that redacts `encrypted_value`, `iv`, and credential patterns

---

## Phase 6: Frontend

### 6.1 New SvelteKit Routes

**Milestone 1:**
```
ui/src/routes/
  podcasts/
    +page.ts              — Load podcast list
    +page.svelte           — Podcast subscription list
    [id]/
      +page.ts            — Load podcast aggregate
      +page.svelte         — Podcast detail (episodes, config)
      settings/
        +page.svelte       — Feed config, credentials, feed token management
  import/
    podcast/
      +page.svelte         — "Add Podcast" flow (enter feed URL → preview → subscribe)
```

**Milestone 2 additions:**
```
ui/src/routes/
  podcasts/[id]/
    sanitization/
      +page.svelte         — Sanitization review queue for this podcast's episodes
```

### 6.2 Key UI Components

**Milestone 1:**
```
ui/src/lib/components/
  PodcastCard.svelte           — Summary card for podcast list
  PodcastEpisodeList.svelte    — Episode table (no sanitization status in M1)
  PodcastSubscribeFlow.svelte  — Multi-step: URL → preview feed → configure → subscribe
  FeedTokenDisplay.svelte      — Copy-to-clipboard RSS URL for podcast players
```

**Milestone 2 additions:**
```
ui/src/lib/components/
  SanitizationReview.svelte    — Side-by-side: original vs sanitized, approve/reject
  SanitizationBadge.svelte     — Status badge for episode list rows
```

### 6.3 Sanitization Review UX (Milestone 2)

Critical for trust. User must be able to:
1. See detected ad segments as colored regions on a waveform/timeline
2. Listen to 5s before/after each cut point
3. Approve all / reject all / approve individually
4. Toggle between sanitized and original audio playback
5. See transcript text for each detected segment

### 0.4 Sidecar Modules

```
libation/            — .NET-based Libation container (Dockerized)
  exports manifests + audio into shared drop directory for Shelf scans
minuspod/            — ttlequals0/minuspod container for ad-removal
```

---

## Phase 1: Data Model & Persistence
...
### 2.3 MinusPod Adapter (`integration/podcast/sanitization/MinusPodAdapter.kt`)
...
### 2.4 Libation Scanner (`integration/podcast/libation/*`)
Shelf consumes Libation export artifacts from a shared filesystem drop directory:
- Parse manifest JSON files
- Validate required fields (`asin`, `title`, `audioFile`)
- Validate relative audio paths stay under the configured drop directory
- Persist run-level summaries in `libation_import_runs`
- Persist per-source idempotency state in `libation_import_records` using `source_key = libation:{asin}`
- Expose scan status via:
  - `POST /podcasts/libation/scan`
  - `GET /podcasts/libation/status`

---

## Implementation Order

### Milestone 1: Podcast Ingestion + RSS Distribution (COMPLETE)
Steps 1-13.

### Milestone 1.5: Libation Container Bridge (COMPLETE)
14. **Infrastructure:** Add `libation` service to compose files.
15. **Scanner Adapter:** Build Kotlin parser/scanner for Libation manifests under `integration/podcast/libation`.
16. **Scheduler:** Add periodic Libation scan scheduler controlled by env configuration.
17. **Frontend Integration:** Replace Audible dashboard widgets/routes with Libation scan + status UI.
18. **Dashboard Integration:** Wire Libation scan status into the Podcasts dashboard model.

### Milestone 2: Commercial Crusher (MinusPod Sidecar)
19. **Infrastructure:** Add `ttlequals0/minuspod` image to `docker-compose.yaml`.
20. **MinusPod Client:** Build Kotlin adapter for MinusPod REST API.
21. **Feed Orchestration:** Update fetch service to swap original URLs for MinusPod proxy URLs.
22. **Status Integration:** Add "Processing" status to Shelf UI based on MinusPod polling.
23. **Manual Review:** Adapt UI to allow users to trigger re-processing or review MinusPod segments.


---

## Open Questions

### Milestone 1
1. **Storage layout** — Episodes under `{storage_root}/podcasts/{series_id}/` or reuse existing book storage layout?
2. **Multi-user** — Podcast subscriptions per-user or shared catalog? Current Book/Series are shared. Recommend shared, matching existing pattern.
3. **Rate limiting RSS endpoint** — Feed readers can be aggressive. Need per-token rate limiting?
4. **Walled garden adapters** — Libation is the supported bridge path for proprietary catalogs; avoid in-app auth adapters.

### Milestone 2
5. **Whisper model size** — `base.en` (141MB) vs `small.en` (466MB)? Accuracy vs. speed tradeoff. Recommend `base.en` default, configurable.
6. **LLM model** — Which Ollama model for ad classification? `llama3.2:3b` should suffice for structured extraction.
7. **Sanitization opt-in default** — `autoSanitize` default `true` or `false` for new subscriptions? Recommend `false` until pipeline is proven reliable.
