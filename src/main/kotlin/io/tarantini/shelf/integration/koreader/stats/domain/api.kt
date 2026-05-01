@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader.stats.domain

import io.tarantini.shelf.catalog.metadata.domain.EditionId
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable

@Serializable
data class KoreaderStatBookResponse(
    val id: KoreaderBookId,
    val editionId: EditionId?,
    val md5: String?,
    val title: String,
    val authors: String?,
    val series: String?,
    val language: String?,
    val pages: Int?,
    val matched: Boolean,
    val firstSeenAt: Instant,
    val lastIngestedAt: Instant,
)

@Serializable
data class KoreaderSessionResponse(
    val id: KoreaderSessionId,
    val startedAt: Instant,
    val endedAt: Instant,
    val durationSeconds: Int,
    val pagesRead: Int,
    val firstPage: Int,
    val lastPage: Int,
)

@Serializable
data class KoreaderBookTotalsResponse(
    val bookSurrogateId: KoreaderBookId,
    val editionId: EditionId?,
    val sessionCount: Long,
    val totalDurationSeconds: Long,
    val totalPagesRead: Long,
    val firstSessionAt: Instant?,
    val lastSessionAt: Instant?,
)

@Serializable
data class KoreaderDailyAggregateResponse(
    val day: Instant,
    val sessionCount: Long,
    val bookCount: Long,
    val totalDurationSeconds: Long,
    val totalPagesRead: Long,
)

@Serializable
data class KoreaderIngestSummaryResponse(
    val booksSeen: Int,
    val booksMatched: Int,
    val booksUnmatched: Int,
    val pagesUpserted: Int,
    val sessionsInserted: Int,
)

fun KoreaderStatBook.toResponse() =
    KoreaderStatBookResponse(
        id = id,
        editionId = editionId,
        md5 = md5?.value,
        title = title,
        authors = authors,
        series = series,
        language = language,
        pages = pages,
        matched = editionId != null,
        firstSeenAt = firstSeenAt,
        lastIngestedAt = lastIngestedAt,
    )

fun KoreaderReadingSession.toResponse() =
    KoreaderSessionResponse(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSeconds = durationSeconds,
        pagesRead = pagesRead,
        firstPage = firstPage,
        lastPage = lastPage,
    )

fun KoreaderBookTotals.toResponse() =
    KoreaderBookTotalsResponse(
        bookSurrogateId = bookSurrogateId,
        editionId = editionId,
        sessionCount = sessionCount,
        totalDurationSeconds = totalDurationSeconds,
        totalPagesRead = totalPagesRead,
        firstSessionAt = firstSessionAt,
        lastSessionAt = lastSessionAt,
    )

fun KoreaderDailyAggregate.toResponse() =
    KoreaderDailyAggregateResponse(
        day = day,
        sessionCount = sessionCount,
        bookCount = bookCount,
        totalDurationSeconds = totalDurationSeconds,
        totalPagesRead = totalPagesRead,
    )

fun KoreaderStatsIngestSummary.toResponse() =
    KoreaderIngestSummaryResponse(
        booksSeen = booksSeen,
        booksMatched = booksMatched,
        booksUnmatched = booksUnmatched,
        pagesUpserted = pagesUpserted,
        sessionsInserted = sessionsInserted,
    )
