@file:OptIn(ExperimentalContextParameters::class, ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import.staging

import app.cash.sqldelight.Transacter
import arrow.core.raise.recover
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.lettuce.core.KeyValue
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.tarantini.shelf.catalog.author.persistence.AuthorQueries
import io.tarantini.shelf.catalog.book.BookAggregateProvider
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.MetadataRepository
import io.tarantini.shelf.catalog.metadata.domain.MediaType
import io.tarantini.shelf.catalog.series.persistence.SeriesQueries
import io.tarantini.shelf.processing.epub.EpubWriter
import io.tarantini.shelf.processing.import.domain.StagedBook
import io.tarantini.shelf.processing.jobs.SyncMetadataWorker
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.auth.JwtToken
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.system.measureTimeMillis
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Suppress("UNCHECKED_CAST")
class StagedListValkeyIsolationTest :
    StringSpec({
        "staged list retrieval stays responsive while worker BRPOP is blocked on a separate connection" {
            val workerCommands = mockk<RedisCommands<String, String>>()
            every { workerCommands.brpop(10, "jobs:sync_metadata") } answers
                {
                    Thread.sleep(500)
                    null as KeyValue<String, String>?
                }

            val workerConnection = mockk<StatefulRedisConnection<String, String>>()
            every { workerConnection.sync() } returns workerCommands

            val userId = UserId.fromRaw(Uuid.random())
            val stagedBook =
                StagedBook(
                    id = "staged-1",
                    userId = userId,
                    title = "Queued Book",
                    authors = listOf("Author"),
                    storagePath = "books/author/queued.epub",
                    genres = emptyList(),
                    mediaType = MediaType.EBOOK,
                    createdAt = "2026-04-20T00:00:00Z",
                )
            val stagedJson = Json.encodeToString(stagedBook)

            val appCommands = mockk<RedisCommands<String, String>>()
            every { appCommands.keys("staged_book:*") } returns listOf("staged_book:staged-1")
            every { appCommands.get("staged_book:staged-1") } returns stagedJson

            val appConnection = mockk<StatefulRedisConnection<String, String>>()
            every { appConnection.sync() } returns appCommands

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val worker =
                    SyncMetadataWorker(
                        scope = scope,
                        bookAggregateProvider = mockk<BookAggregateProvider>(relaxed = true),
                        epubWriter = mockk<EpubWriter>(relaxed = true),
                        storageService = mockk<StorageService>(relaxed = true),
                        valkeyConnection = workerConnection,
                    )
                worker.start()
                delay(50)

                val service =
                    stagedBookService(
                        stagedBookStore = valkeyStagedBookStore(appConnection),
                        storageService = mockk<StorageService>(relaxed = true),
                        bookQueries = mockk<BookQueries>(relaxed = true),
                        authorQueries = mockk<AuthorQueries>(relaxed = true),
                        seriesQueries = mockk<SeriesQueries>(relaxed = true),
                        metadataRepository = mockk<MetadataRepository>(relaxed = true),
                        scope = scope,
                        transacter = mockk<Transacter>(relaxed = true),
                    )

                val elapsedMs = measureTimeMillis {
                    recover({
                        with(this) {
                            with(JwtContext(JwtToken("test"), userId)) {
                                val page = service.getAll(page = 0, size = 20)
                                page.items.size
                            }
                        }
                    }) {
                        throw AssertionError("Expected staged list retrieval to succeed: $it")
                    }
                }

                elapsedMs.shouldBeLessThan(200)
            } finally {
                scope.cancel()
            }
        }
    })
