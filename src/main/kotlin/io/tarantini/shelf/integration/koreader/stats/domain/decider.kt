@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader.stats.domain

import io.tarantini.shelf.catalog.metadata.domain.EditionId
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

const val DEFAULT_SESSION_GAP_SECONDS: Long = 600L

object EditionMatcher {
    fun decide(md5: Md5Hash?, lookup: (Md5Hash) -> EditionId?): EditionMatch =
        if (md5 == null) EditionMatch.Unmatched
        else lookup(md5)?.let(EditionMatch::Matched) ?: EditionMatch.Unmatched
}

sealed interface EditionMatch {
    data class Matched(val editionId: EditionId) : EditionMatch

    data object Unmatched : EditionMatch
}

object PageRescaler {
    fun rescale(row: KoreaderPageStatRow, currentPages: Int?): Sequence<KoreaderPageStatRow> {
        val totalAtRead = row.totalPagesAtRead
        val needsRescale =
            totalAtRead != null &&
                currentPages != null &&
                currentPages > 0 &&
                totalAtRead > 0 &&
                currentPages != totalAtRead
        if (!needsRescale) return sequenceOf(row)
        return rescaleRow(row, requireNotNull(totalAtRead), requireNotNull(currentPages))
    }

    private fun rescaleRow(
        row: KoreaderPageStatRow,
        totalAtRead: Int,
        currentPages: Int,
    ): Sequence<KoreaderPageStatRow> {
        val firstPage = ((row.page - 1).toLong() * currentPages) / totalAtRead + 1
        val lastPage =
            maxOf(firstPage, (row.page.toLong() * currentPages) / totalAtRead)
                .coerceAtMost(currentPages.toLong())
        val span = (lastPage - firstPage + 1).toInt().coerceAtLeast(1)
        val perPageDuration = (row.durationSeconds / span).coerceAtLeast(0)
        return (0 until span).asSequence().map { offset ->
            KoreaderPageStatRow(
                page = (firstPage + offset).toInt(),
                startTimeEpoch = row.startTimeEpoch,
                durationSeconds = perPageDuration,
                totalPagesAtRead = currentPages,
            )
        }
    }
}

object SessionAggregator {
    fun aggregate(
        rows: List<KoreaderPageStatRow>,
        bookSurrogateId: KoreaderBookId,
        gapSeconds: Long = DEFAULT_SESSION_GAP_SECONDS,
        idGenerator: () -> KoreaderSessionId = KoreaderSessionId::random,
    ): List<KoreaderReadingSession> {
        if (rows.isEmpty()) return emptyList()
        val sorted = rows.sortedBy { it.startTimeEpoch }
        val sessions = mutableListOf<KoreaderReadingSession>()
        var current: WorkingSession? = null

        for (row in sorted) {
            val rowEnd = row.startTimeEpoch + row.durationSeconds
            val active = current
            if (active == null || row.startTimeEpoch - active.endEpoch > gapSeconds) {
                if (active != null) sessions += active.toSession(bookSurrogateId, idGenerator())
                current =
                    WorkingSession(
                        startEpoch = row.startTimeEpoch,
                        endEpoch = rowEnd,
                        durationSeconds = row.durationSeconds,
                        pagesRead = 1,
                        firstPage = row.page,
                        lastPage = row.page,
                    )
            } else {
                current =
                    active.copy(
                        endEpoch = maxOf(active.endEpoch, rowEnd),
                        durationSeconds = active.durationSeconds + row.durationSeconds,
                        pagesRead = active.pagesRead + 1,
                        firstPage = minOf(active.firstPage, row.page),
                        lastPage = maxOf(active.lastPage, row.page),
                    )
            }
        }
        current?.let { sessions += it.toSession(bookSurrogateId, idGenerator()) }
        return sessions
    }

    private data class WorkingSession(
        val startEpoch: Long,
        val endEpoch: Long,
        val durationSeconds: Int,
        val pagesRead: Int,
        val firstPage: Int,
        val lastPage: Int,
    ) {
        fun toSession(
            bookSurrogateId: KoreaderBookId,
            id: KoreaderSessionId,
        ): KoreaderReadingSession =
            KoreaderReadingSession(
                id = id,
                bookSurrogateId = bookSurrogateId,
                startedAt = Instant.fromEpochSeconds(startEpoch),
                endedAt = Instant.fromEpochSeconds(endEpoch),
                durationSeconds = durationSeconds,
                pagesRead = pagesRead,
                firstPage = firstPage,
                lastPage = lastPage,
            )
    }
}
