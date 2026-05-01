@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader.stats.domain

import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

data class KoreaderStatBook(
    val id: KoreaderBookId,
    val userId: UserId,
    val editionId: EditionId?,
    val md5: Md5Hash?,
    val title: String,
    val authors: String?,
    val series: String?,
    val language: String?,
    val pages: Int?,
    val sourceTotalReadTime: Long?,
    val sourceTotalReadPages: Int?,
    val sourceLastOpen: Long?,
    val firstSeenAt: Instant,
    val lastIngestedAt: Instant,
)

data class KoreaderPageStatRow(
    val page: Int,
    val startTimeEpoch: Long,
    val durationSeconds: Int,
    val totalPagesAtRead: Int?,
)

data class KoreaderReadingSession(
    val id: KoreaderSessionId,
    val bookSurrogateId: KoreaderBookId,
    val startedAt: Instant,
    val endedAt: Instant,
    val durationSeconds: Int,
    val pagesRead: Int,
    val firstPage: Int,
    val lastPage: Int,
)

data class KoreaderBookTotals(
    val bookSurrogateId: KoreaderBookId,
    val editionId: EditionId?,
    val sessionCount: Long,
    val totalDurationSeconds: Long,
    val totalPagesRead: Long,
    val firstSessionAt: Instant?,
    val lastSessionAt: Instant?,
)

data class KoreaderDailyAggregate(
    val day: Instant,
    val sessionCount: Long,
    val bookCount: Long,
    val totalDurationSeconds: Long,
    val totalPagesRead: Long,
)

data class KoreaderStatsIngestSummary(
    val booksSeen: Int,
    val booksMatched: Int,
    val booksUnmatched: Int,
    val pagesUpserted: Int,
    val sessionsInserted: Int,
)
