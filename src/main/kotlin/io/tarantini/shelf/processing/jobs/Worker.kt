package io.tarantini.shelf.processing.jobs

import arrow.core.raise.either
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.api.StatefulRedisConnection
import io.tarantini.shelf.catalog.book.BookService
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.processing.epub.EpubMetadataUpdates
import io.tarantini.shelf.processing.epub.EpubWriter
import io.tarantini.shelf.processing.storage.StorageService
import java.nio.file.Files
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

private val logger = KotlinLogging.logger {}

class SyncMetadataWorker(
    private val scope: CoroutineScope,
    private val bookService: BookService,
    private val epubWriter: EpubWriter,
    private val storageService: StorageService,
    private val valkeyConnection: StatefulRedisConnection<String, String>? = null,
    private val inMemoryChannel: Channel<BookId>? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @OptIn(ExperimentalUuidApi::class)
    fun start() {
        scope.launch(dispatcher) {
            logger.info { "Starting SyncMetadataWorker..." }
            while (isActive) {
                try {
                    val bookId =
                        if (valkeyConnection != null) {
                            val commands = valkeyConnection.sync()
                            val result = commands.brpop(10, "jobs:sync_metadata")
                            result?.let { BookId.fromRaw(it.value) }
                        } else if (inMemoryChannel != null) {
                            inMemoryChannel.receive()
                        } else {
                            null
                        }

                    if (bookId != null) {
                        processSyncJob(bookId)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error in SyncMetadataWorker loop" }
                }
            }
        }
    }

    private suspend fun processSyncJob(bookId: BookId) {
        logger.info { "Processing metadata sync for book: $bookId" }

        either {
                val aggregate = bookService.getBookAggregate(bookId)
                val metadataAggregate = aggregate.metadata ?: return@either

                // Currently we only support EPUB sync
                val ebookEntry =
                    metadataAggregate.editions.find { it.edition.format == BookFormat.EBOOK }
                        ?: return@either
                val ebook = ebookEntry.edition
                val metadata = metadataAggregate.metadata

                val filePath = storageService.resolve(ebook.path)
                if (Files.exists(filePath)) {
                    val updates =
                        EpubMetadataUpdates(
                            title = metadata.title,
                            authors = aggregate.authors.map { it.name },
                            description = metadata.description,
                            publisher = metadata.publisher,
                            publishYear = metadata.published,
                        )

                    epubWriter.updateMetadata(filePath, updates)
                    logger.info { "Successfully synced metadata for book: $bookId to $filePath" }
                } else {
                    logger.warn { "File not found for metadata sync: $filePath" }
                }
            }
            .mapLeft { error ->
                logger.error { "Failed to sync metadata for book: $bookId: ${error}" }
            }
    }
}
