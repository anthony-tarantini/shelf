@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader.stats

import arrow.core.raise.catch
import arrow.core.raise.context.either
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.metadata.FileHashRelinkPort
import io.tarantini.shelf.catalog.metadata.MetadataRepository
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.integration.koreader.stats.domain.EditionMatch
import io.tarantini.shelf.integration.koreader.stats.domain.EditionMatcher
import io.tarantini.shelf.integration.koreader.stats.domain.IngestKoreaderStatsCommand
import io.tarantini.shelf.integration.koreader.stats.domain.InvalidStatsSqliteFile
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderBookId
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderBookTotals
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderDailyAggregate
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderReadingSession
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderSourceBook
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderStatBook
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderStatsDateRange
import io.tarantini.shelf.integration.koreader.stats.domain.KoreaderStatsIngestSummary
import io.tarantini.shelf.integration.koreader.stats.domain.Md5Hash
import io.tarantini.shelf.integration.koreader.stats.domain.PageRescaler
import io.tarantini.shelf.integration.koreader.stats.domain.SessionAggregator
import io.tarantini.shelf.integration.koreader.stats.ingest.KoreaderSqliteReader
import io.tarantini.shelf.integration.koreader.stats.ingest.openKoreaderSqlite
import io.tarantini.shelf.user.identity.domain.UserId
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

interface KoreaderStatsService : FileHashRelinkPort {
    context(_: RaiseContext)
    suspend fun ingest(command: IngestKoreaderStatsCommand): KoreaderStatsIngestSummary

    context(_: RaiseContext)
    suspend fun listBooks(userId: UserId): List<KoreaderStatBook>

    context(_: RaiseContext)
    suspend fun listUnmatchedBooks(userId: UserId): List<KoreaderStatBook>

    context(_: RaiseContext)
    suspend fun sessionsForEdition(
        userId: UserId,
        editionId: EditionId,
        range: KoreaderStatsDateRange?,
    ): List<KoreaderReadingSession>

    context(_: RaiseContext)
    suspend fun totalsForEdition(userId: UserId, editionId: EditionId): KoreaderBookTotals?

    context(_: RaiseContext)
    suspend fun dailyAggregate(
        userId: UserId,
        range: KoreaderStatsDateRange,
    ): List<KoreaderDailyAggregate>

    context(_: RaiseContext)
    override suspend fun relinkByFileHash(fileHash: String)
}

fun koreaderStatsService(
    repository: KoreaderStatsRepository,
    metadataRepository: MetadataRepository,
    clock: Clock = Clock.System,
    sqliteOpener: (Path) -> KoreaderSqliteReader = ::openKoreaderSqlite,
): KoreaderStatsService =
    DefaultKoreaderStatsService(repository, metadataRepository, clock, sqliteOpener)

private class DefaultKoreaderStatsService(
    private val repository: KoreaderStatsRepository,
    private val metadataRepository: MetadataRepository,
    private val clock: Clock,
    private val sqliteOpener: (Path) -> KoreaderSqliteReader,
) : KoreaderStatsService {
    private val logger = LoggerFactory.getLogger("io.tarantini.shelf.koreader.stats.service")

    context(_: RaiseContext)
    override suspend fun ingest(command: IngestKoreaderStatsCommand): KoreaderStatsIngestSummary =
        withContext(Dispatchers.IO) {
            val reader =
                catch({ sqliteOpener(command.sourcePath) }) { error ->
                    logger.warn(
                        "KOReader stats sqlite open failed error={}",
                        error::class.simpleName,
                    )
                    raise(InvalidStatsSqliteFile)
                }
            reader.use {
                catch({ ingestWithReader(command.userId, it) }) { error ->
                    logger.warn(
                        "KOReader stats sqlite read failed error={}",
                        error::class.simpleName,
                    )
                    raise(InvalidStatsSqliteFile)
                }
            }
        }

    context(_: RaiseContext)
    private suspend fun ingestWithReader(
        userId: UserId,
        reader: KoreaderSqliteReader,
    ): KoreaderStatsIngestSummary {
        val books = reader.listBooks()
        var matched = 0
        var unmatched = 0
        var pagesUpserted = 0
        var sessionsInserted = 0

        for (sourceBook in books) {
            val matchResult =
                EditionMatcher.decide(sourceBook.md5) { md5 ->
                    metadataRepository.selectEditionByFileHash(md5.value)?.id?.id
                }
            val editionId =
                when (matchResult) {
                    is EditionMatch.Matched -> {
                        matched++
                        matchResult.editionId
                    }
                    EditionMatch.Unmatched -> {
                        unmatched++
                        null
                    }
                }

            val surrogateId = resolveSurrogateId(userId, sourceBook.md5)
            persistBook(userId, surrogateId, editionId, sourceBook)

            val rawRows = reader.pageStatRowsForBook(sourceBook.sourceId)
            val rescaled =
                rawRows.flatMap { row -> PageRescaler.rescale(row, sourceBook.pages).toList() }
            rescaled.forEach { repository.upsertPage(userId, surrogateId, it) }
            pagesUpserted += rescaled.size

            val sessions = SessionAggregator.aggregate(rescaled, surrogateId)
            repository.replaceSessionsForBook(userId, surrogateId, sessions)
            sessionsInserted += sessions.size
        }

        logger.info(
            "KOReader stats ingest complete books={} matched={} unmatched={} pages={} sessions={}",
            books.size,
            matched,
            unmatched,
            pagesUpserted,
            sessionsInserted,
        )

        return KoreaderStatsIngestSummary(
            booksSeen = books.size,
            booksMatched = matched,
            booksUnmatched = unmatched,
            pagesUpserted = pagesUpserted,
            sessionsInserted = sessionsInserted,
        )
    }

    context(_: RaiseContext)
    private suspend fun resolveSurrogateId(userId: UserId, md5: Md5Hash?): KoreaderBookId =
        if (md5 != null) {
            repository.findBookByUserAndMd5(userId, md5)?.id ?: KoreaderBookId.random()
        } else {
            KoreaderBookId.random()
        }

    context(_: RaiseContext)
    private suspend fun persistBook(
        userId: UserId,
        surrogateId: KoreaderBookId,
        editionId: EditionId?,
        sourceBook: KoreaderSourceBook,
    ) {
        val now = clock.now()
        val existing = sourceBook.md5?.let { repository.findBookByUserAndMd5(userId, it) }
        repository.upsertBook(
            KoreaderStatBook(
                id = surrogateId,
                userId = userId,
                editionId = editionId,
                md5 = sourceBook.md5,
                title = sourceBook.title,
                authors = sourceBook.authors,
                series = sourceBook.series,
                language = sourceBook.language,
                pages = sourceBook.pages,
                sourceTotalReadTime = sourceBook.totalReadTime,
                sourceTotalReadPages = sourceBook.totalReadPages,
                sourceLastOpen = sourceBook.lastOpen,
                firstSeenAt = existing?.firstSeenAt ?: now,
                lastIngestedAt = now,
            )
        )
    }

    context(_: RaiseContext)
    override suspend fun listBooks(userId: UserId): List<KoreaderStatBook> =
        withContext(Dispatchers.IO) { repository.listBooksByUser(userId) }

    context(_: RaiseContext)
    override suspend fun listUnmatchedBooks(userId: UserId): List<KoreaderStatBook> =
        withContext(Dispatchers.IO) { repository.listUnmatchedBooksByUser(userId) }

    context(_: RaiseContext)
    override suspend fun sessionsForEdition(
        userId: UserId,
        editionId: EditionId,
        range: KoreaderStatsDateRange?,
    ): List<KoreaderReadingSession> =
        withContext(Dispatchers.IO) {
            val book =
                ensureNotNull(repository.findBookByUserAndEdition(userId, editionId)) {
                    io.tarantini.shelf.integration.koreader.stats.domain.KoreaderStatsBookNotFound
                }
            repository.listSessionsForBook(userId, book.id, range?.from, range?.to)
        }

    context(_: RaiseContext)
    override suspend fun totalsForEdition(
        userId: UserId,
        editionId: EditionId,
    ): KoreaderBookTotals? =
        withContext(Dispatchers.IO) {
            val book =
                repository.findBookByUserAndEdition(userId, editionId) ?: return@withContext null
            repository.bookTotals(userId, book.id, book.editionId)
        }

    context(_: RaiseContext)
    override suspend fun dailyAggregate(
        userId: UserId,
        range: KoreaderStatsDateRange,
    ): List<KoreaderDailyAggregate> =
        withContext(Dispatchers.IO) { repository.dailyAggregates(userId, range.from, range.to) }

    context(_: RaiseContext)
    override suspend fun relinkByFileHash(fileHash: String) {
        val md5 = Md5Hash.fromRawOrNull(fileHash) ?: return
        either {
                val edition = metadataRepository.selectEditionByFileHash(md5.value) ?: return@either
                withContext(Dispatchers.IO) { repository.relinkByMd5(md5, edition.id.id) }
            }
            .onLeft { error ->
                logger.warn(
                    "KOReader stats relink failed fileHash error={}",
                    error::class.simpleName,
                )
            }
    }
}
