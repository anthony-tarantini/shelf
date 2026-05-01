@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader.stats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderBookId
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderPageStatRow
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderSessionId
import io.tarantini.shelf.integration.koreader.stats.domain.SessionAggregator
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val bookId = KoreaderBookId.fromRaw(Uuid.random())

private fun row(page: Int, start: Long, duration: Int = 60) =
    KoreaderPageStatRow(
        page = page,
        startTimeEpoch = start,
        durationSeconds = duration,
        totalPagesAtRead = null,
    )

private fun seqId(prefix: Long): () -> KoreaderSessionId {
    var n = 0L
    return {
        n += 1
        KoreaderSessionId.fromRaw(Uuid.fromLongs(prefix, n))
    }
}

class SessionAggregatorTest :
    StringSpec({
        "empty input returns empty sessions" {
            SessionAggregator.aggregate(emptyList(), bookId).shouldBeEmpty()
        }

        "single row produces one session" {
            val sessions =
                SessionAggregator.aggregate(
                    rows = listOf(row(1, 1000, 30)),
                    bookSurrogateId = bookId,
                    idGenerator = seqId(1L),
                )
            sessions.size shouldBe 1
            sessions[0].pagesRead shouldBe 1
            sessions[0].durationSeconds shouldBe 30
            sessions[0].firstPage shouldBe 1
            sessions[0].lastPage shouldBe 1
        }

        "rows within gap merge into single session" {
            val rows = listOf(row(1, 1000, 60), row(2, 1100, 60), row(3, 1200, 60))
            val sessions = SessionAggregator.aggregate(rows, bookId, idGenerator = seqId(2L))
            sessions.size shouldBe 1
            sessions[0].pagesRead shouldBe 3
            sessions[0].durationSeconds shouldBe 180
            sessions[0].firstPage shouldBe 1
            sessions[0].lastPage shouldBe 3
        }

        "rows past gap split into separate sessions" {
            val rows = listOf(row(1, 1000, 30), row(2, 5000, 30))
            val sessions =
                SessionAggregator.aggregate(rows, bookId, gapSeconds = 600, idGenerator = seqId(3L))
            sessions.size shouldBe 2
            sessions[0].pagesRead shouldBe 1
            sessions[1].pagesRead shouldBe 1
        }

        "out-of-order input is sorted by start time" {
            val rows = listOf(row(2, 1500, 60), row(1, 1000, 60))
            val sessions = SessionAggregator.aggregate(rows, bookId, idGenerator = seqId(4L))
            sessions.size shouldBe 1
            sessions[0].firstPage shouldBe 1
            sessions[0].lastPage shouldBe 2
        }

        "duration is sum of per-page durations not span" {
            val rows = listOf(row(1, 1000, 30), row(2, 1500, 90))
            val sessions = SessionAggregator.aggregate(rows, bookId, idGenerator = seqId(5L))
            sessions.size shouldBe 1
            sessions[0].durationSeconds shouldBe 120
        }
    })
