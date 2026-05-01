@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader.stats

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.integration.koreader.persistence.KoreaderStatsQueries
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderBookId
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderBookTotals
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderDailyAggregate
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderPageStatRow
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderReadingSession
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderStatBook
import io.tarantini.shelf.integration.koreader.stats.domain.Md5Hash
import io.tarantini.shelf.user.identity.domain.UserId
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi

interface KoreaderStatsRepository {
    context(_: RaiseContext)
    suspend fun upsertBook(book: KoreaderStatBook)

    context(_: RaiseContext)
    suspend fun findBookByUserAndMd5(userId: UserId, md5: Md5Hash): KoreaderStatBook?

    context(_: RaiseContext)
    suspend fun listBooksByUser(userId: UserId): List<KoreaderStatBook>

    context(_: RaiseContext)
    suspend fun listUnmatchedBooksByUser(userId: UserId): List<KoreaderStatBook>

    context(_: RaiseContext)
    suspend fun findBookByUserAndEdition(userId: UserId, editionId: EditionId): KoreaderStatBook?

    context(_: RaiseContext)
    suspend fun relinkByMd5(md5: Md5Hash, editionId: EditionId)

    context(_: RaiseContext)
    suspend fun upsertPage(
        userId: UserId,
        bookSurrogateId: KoreaderBookId,
        row: KoreaderPageStatRow,
    )

    context(_: RaiseContext)
    suspend fun replaceSessionsForBook(
        userId: UserId,
        bookSurrogateId: KoreaderBookId,
        sessions: List<KoreaderReadingSession>,
    )

    context(_: RaiseContext)
    suspend fun listSessionsForBook(
        userId: UserId,
        bookSurrogateId: KoreaderBookId,
        from: Instant?,
        to: Instant?,
    ): List<KoreaderReadingSession>

    context(_: RaiseContext)
    suspend fun bookTotals(
        userId: UserId,
        bookSurrogateId: KoreaderBookId,
        editionId: EditionId?,
    ): KoreaderBookTotals

    context(_: RaiseContext)
    suspend fun dailyAggregates(
        userId: UserId,
        from: Instant,
        to: Instant,
    ): List<KoreaderDailyAggregate>
}

fun koreaderStatsRepository(queries: KoreaderStatsQueries): KoreaderStatsRepository =
    SqlDelightKoreaderStatsRepository(queries)

private class SqlDelightKoreaderStatsRepository(private val queries: KoreaderStatsQueries) :
    KoreaderStatsRepository {
    context(_: RaiseContext)
    override suspend fun upsertBook(book: KoreaderStatBook) {
        queries.upsertStatBook(
            id = book.id,
            userId = book.userId,
            editionId = book.editionId,
            md5 = book.md5?.value,
            title = book.title,
            authors = book.authors,
            series = book.series,
            language = book.language,
            pages = book.pages,
            sourceTotalReadTime = book.sourceTotalReadTime,
            sourceTotalReadPages = book.sourceTotalReadPages,
            sourceLastOpen = book.sourceLastOpen,
        )
    }

    context(_: RaiseContext)
    override suspend fun findBookByUserAndMd5(userId: UserId, md5: Md5Hash): KoreaderStatBook? =
        queries.selectStatBookByUserAndMd5(userId, md5.value).executeAsOneOrNull()?.toDomain()

    context(_: RaiseContext)
    override suspend fun listBooksByUser(userId: UserId): List<KoreaderStatBook> =
        queries.selectStatBooksByUser(userId).executeAsList().map { it.toDomain() }

    context(_: RaiseContext)
    override suspend fun listUnmatchedBooksByUser(userId: UserId): List<KoreaderStatBook> =
        queries.selectUnmatchedStatBooksByUser(userId).executeAsList().map { it.toDomain() }

    context(_: RaiseContext)
    override suspend fun findBookByUserAndEdition(
        userId: UserId,
        editionId: EditionId,
    ): KoreaderStatBook? =
        queries.selectStatBookByUserAndEdition(userId, editionId).executeAsOneOrNull()?.toDomain()

    context(_: RaiseContext)
    override suspend fun relinkByMd5(md5: Md5Hash, editionId: EditionId) {
        queries.relinkStatBooksByMd5(editionId, md5.value)
    }

    context(_: RaiseContext)
    override suspend fun upsertPage(
        userId: UserId,
        bookSurrogateId: KoreaderBookId,
        row: KoreaderPageStatRow,
    ) {
        queries.upsertStatPage(
            userId = userId,
            bookSurrogateId = bookSurrogateId,
            page = row.page,
            startTime = row.startTimeEpoch,
            durationSeconds = row.durationSeconds,
            totalPages = row.totalPagesAtRead,
        )
    }

    context(_: RaiseContext)
    override suspend fun replaceSessionsForBook(
        userId: UserId,
        bookSurrogateId: KoreaderBookId,
        sessions: List<KoreaderReadingSession>,
    ) {
        queries.transaction {
            queries.deleteStatSessionsForBook(userId, bookSurrogateId)
            sessions.forEach { session ->
                queries.insertStatSession(
                    id = session.id,
                    userId = userId,
                    bookSurrogateId = session.bookSurrogateId,
                    startedAt = session.startedAt.toOffsetDateTime(),
                    endedAt = session.endedAt.toOffsetDateTime(),
                    durationSeconds = session.durationSeconds,
                    pagesRead = session.pagesRead,
                    firstPage = session.firstPage,
                    lastPage = session.lastPage,
                )
            }
        }
    }

    context(_: RaiseContext)
    override suspend fun listSessionsForBook(
        userId: UserId,
        bookSurrogateId: KoreaderBookId,
        from: Instant?,
        to: Instant?,
    ): List<KoreaderReadingSession> {
        val rows =
            if (from != null && to != null) {
                queries
                    .selectStatSessionsForBookBetween(
                        userId = userId,
                        bookSurrogateId = bookSurrogateId,
                        fromTime = from.toOffsetDateTime(),
                        toTime = to.toOffsetDateTime(),
                    )
                    .executeAsList()
            } else {
                queries.selectStatSessionsForBook(userId, bookSurrogateId).executeAsList()
            }
        return rows.map {
            KoreaderReadingSession(
                id = it.id,
                bookSurrogateId = it.book_surrogate_id,
                startedAt = it.started_at.toKotlinInstant(),
                endedAt = it.ended_at.toKotlinInstant(),
                durationSeconds = it.duration_seconds,
                pagesRead = it.pages_read,
                firstPage = it.first_page,
                lastPage = it.last_page,
            )
        }
    }

    context(_: RaiseContext)
    override suspend fun bookTotals(
        userId: UserId,
        bookSurrogateId: KoreaderBookId,
        editionId: EditionId?,
    ): KoreaderBookTotals {
        val row = queries.selectStatTotalsForBook(userId, bookSurrogateId).executeAsOne()
        return KoreaderBookTotals(
            bookSurrogateId = bookSurrogateId,
            editionId = editionId,
            sessionCount = row.session_count,
            totalDurationSeconds = row.total_duration_seconds,
            totalPagesRead = row.total_pages_read,
            firstSessionAt = row.first_session_at?.toKotlinInstant(),
            lastSessionAt = row.last_session_at?.toKotlinInstant(),
        )
    }

    context(_: RaiseContext)
    override suspend fun dailyAggregates(
        userId: UserId,
        from: Instant,
        to: Instant,
    ): List<KoreaderDailyAggregate> =
        queries
            .selectStatDailyAggregatesBetween(
                userId,
                from.toOffsetDateTime(),
                to.toOffsetDateTime(),
            )
            .executeAsList()
            .map {
                KoreaderDailyAggregate(
                    day = it.day.toKotlinInstant(),
                    sessionCount = it.session_count,
                    bookCount = it.book_count,
                    totalDurationSeconds = it.total_duration_seconds,
                    totalPagesRead = it.total_pages_read,
                )
            }

    private fun io.tarantini.shelf.integration.koreader.persistence.Koreader_stat_books.toDomain():
        KoreaderStatBook =
        KoreaderStatBook(
            id = id,
            userId = user_id,
            editionId = edition_id,
            md5 = md5?.let { Md5Hash.fromRawOrNull(it) },
            title = title,
            authors = authors,
            series = series,
            language = language,
            pages = pages,
            sourceTotalReadTime = source_total_read_time,
            sourceTotalReadPages = source_total_read_pages,
            sourceLastOpen = source_last_open,
            firstSeenAt = first_seen_at.toKotlinInstant(),
            lastIngestedAt = last_ingested_at.toKotlinInstant(),
        )
}

private fun Instant.toOffsetDateTime(): OffsetDateTime =
    OffsetDateTime.ofInstant(this.toJavaInstant(), ZoneOffset.UTC)

private fun OffsetDateTime.toKotlinInstant(): Instant =
    Instant.fromEpochMilliseconds(this.toInstant().toEpochMilli())
