@file:OptIn(
    ExperimentalUuidApi::class,
    ExperimentalContextParameters::class,
    ExperimentalCoroutinesApi::class,
)

package io.tarantini.shelf.processing.jobs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.domain.AuthorRoot
import io.tarantini.shelf.catalog.book.BookService
import io.tarantini.shelf.catalog.book.domain.BookAggregate
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookRoot
import io.tarantini.shelf.catalog.metadata.domain.*
import io.tarantini.shelf.processing.epub.EpubMetadataUpdates
import io.tarantini.shelf.processing.epub.EpubWriter
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.processing.storage.StorageService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*

class SyncMetadataWorkerTest :
    StringSpec({
        "SyncMetadataWorker should process job from channel and update epub metadata" {
            val testScope = TestScope()
            val channel = Channel<BookId>(1)

            val bookId = BookId.fromRaw(Uuid.random())
            val storagePath = StoragePath.fromRaw("books/test.epub")

            val metadataAggregate =
                MetadataAggregate<PersistenceState.Persisted>(
                    metadata =
                        MetadataRoot(
                            id =
                                Identity.Persisted(MetadataId.fromRaw(java.util.UUID.randomUUID())),
                            bookId = bookId,
                            title = "Updated Title",
                            description = "Updated Description",
                            publisher = "Updated Publisher",
                            published = 2024,
                        ),
                    editions =
                        listOf(
                            EditionWithChapters(
                                edition =
                                    Edition(
                                        id =
                                            Identity.Persisted(
                                                EditionId.fromRaw(java.util.UUID.randomUUID())
                                            ),
                                        bookId = bookId,
                                        format = BookFormat.EBOOK,
                                        path = storagePath,
                                        size = 1024,
                                    )
                            )
                        ),
                )

            val bookAggregate =
                BookAggregate<PersistenceState.Persisted>(
                    book = BookRoot.fromRaw(bookId, "Old Title", storagePath),
                    authors =
                        listOf(
                            AuthorRoot.fromRaw(
                                AuthorId.fromRaw(java.util.UUID.randomUUID()),
                                "Test Author",
                            )
                        ),
                    metadata = metadataAggregate,
                )

            val bookService = mockk<BookService>()
            val epubWriter = mockk<EpubWriter>()
            val storageService = mockk<StorageService>()

            val tempFile = kotlin.io.path.createTempFile("test", ".epub")
            try {
                coEvery {
                    with(any<RaiseContext>()) { bookService.getBookAggregate(bookId) }
                } returns bookAggregate
                every { storageService.resolve(storagePath) } returns tempFile
                coEvery {
                    with(any<RaiseContext>()) { epubWriter.updateMetadata(tempFile, any()) }
                } just runs

                val worker =
                    SyncMetadataWorker(
                        scope = testScope,
                        bookService = bookService,
                        epubWriter = epubWriter,
                        storageService = storageService,
                        inMemoryChannel = channel,
                        dispatcher = StandardTestDispatcher(testScope.testScheduler),
                    )

                worker.start()
                channel.send(bookId)

                // Give the worker a moment to process
                testScope.advanceUntilIdle()

                val updatesSlot = slot<EpubMetadataUpdates>()
                coVerify {
                    with(any<RaiseContext>()) {
                        epubWriter.updateMetadata(tempFile, capture(updatesSlot))
                    }
                }

                updatesSlot.captured shouldBe
                    EpubMetadataUpdates(
                        title = "Updated Title",
                        authors = listOf("Test Author"),
                        description = "Updated Description",
                        publisher = "Updated Publisher",
                        publishYear = 2024,
                    )
            } finally {
                java.nio.file.Files.deleteIfExists(tempFile)
            }
        }
    })
