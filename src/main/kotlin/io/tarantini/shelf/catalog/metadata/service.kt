package io.tarantini.shelf.catalog.metadata

import arrow.core.getOrElse
import arrow.core.raise.context.ensure
import arrow.core.raise.either
import arrow.fx.coroutines.ResourceScope
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.*
import io.tarantini.shelf.integration.core.ExternalMetadataProvider
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MetadataProvider {
    context(_: RaiseContext)
    suspend fun getMetadataForBook(bookId: BookId): SavedMetadataAggregate?

    context(_: RaiseContext)
    suspend fun getMetadataForBooks(bookIds: List<BookId>): Map<BookId, SavedMetadataAggregate>

    context(_: RaiseContext)
    suspend fun getBookIdsByFormat(format: BookFormat): List<BookId>

    context(_: RaiseContext)
    suspend fun getExternalMetadata(query: String): List<ExternalMetadata>
}

interface MetadataModifier {
    context(_: RaiseContext)
    suspend fun save(metadata: NewMetadataAggregate): MetadataId

    context(_: RaiseContext)
    suspend fun processAndSave(
        scope: ResourceScope,
        sourcePath: Path,
        fileName: String,
        bookId: BookId,
    ): MetadataId

    context(_: RaiseContext)
    suspend fun deleteByBookId(bookId: BookId)
}

interface MetadataService : MetadataProvider, MetadataModifier

interface FileHashRelinkPort {
    context(_: RaiseContext)
    suspend fun relinkByFileHash(fileHash: String)
}

private object NoOpFileHashRelinkPort : FileHashRelinkPort {
    context(_: RaiseContext)
    override suspend fun relinkByFileHash(fileHash: String) = Unit
}

fun metadataService(
    externalMetadataProvider: ExternalMetadataProvider,
    metadataProcessor: MetadataProcessor,
    repository: MetadataRepository,
    decider: MetadataDecider = DefaultMetadataDecider,
    relinkPort: FileHashRelinkPort = NoOpFileHashRelinkPort,
): MetadataService =
    MetadataAggregateService(
        externalMetadataProvider,
        metadataProcessor,
        repository,
        decider,
        relinkPort,
    )

private class MetadataAggregateService(
    private val externalMetadataProvider: ExternalMetadataProvider,
    private val metadataProcessor: MetadataProcessor,
    private val repository: MetadataRepository,
    private val decider: MetadataDecider,
    private val relinkPort: FileHashRelinkPort,
) : MetadataService {
    context(_: RaiseContext)
    override suspend fun getMetadataForBook(bookId: BookId): SavedMetadataAggregate? =
        withContext(Dispatchers.IO) {
            either {
                    val metadataRecord = repository.getMetadataByBookId(bookId)
                    val editions =
                        repository.getEditionsByBookId(bookId).map { edition ->
                            EditionWithChapters(
                                edition = edition,
                                chapters = repository.getChaptersByEditionId(edition.id.id),
                            )
                        }

                    MetadataAggregate(metadata = metadataRecord, editions = editions)
                }
                .getOrElse {
                    ensure(it is MetadataNotFound) { it }
                    null
                }
        }

    context(_: RaiseContext)
    override suspend fun getMetadataForBooks(bookIds: List<BookId>) =
        withContext(Dispatchers.IO) { repository.getMetadataForBooks(bookIds) }

    context(_: RaiseContext)
    override suspend fun getBookIdsByFormat(format: BookFormat) =
        withContext(Dispatchers.IO) { repository.getBookIdsByFormat(format) }

    context(_: RaiseContext)
    override suspend fun getExternalMetadata(query: String) =
        externalMetadataProvider.searchBookMetadataByName(query)

    context(_: RaiseContext)
    override suspend fun save(metadata: NewMetadataAggregate): MetadataId {
        val id = withContext(Dispatchers.IO) { repository.saveAggregate(metadata) }
        relinkFileHashes(metadata)
        return id
    }

    context(_: RaiseContext)
    override suspend fun processAndSave(
        scope: ResourceScope,
        sourcePath: Path,
        fileName: String,
        bookId: BookId,
    ): MetadataId {
        val processed = metadataProcessor.process(scope, sourcePath, fileName, bookId)
        val aggregate = decider.planAggregate(processed)
        val id = withContext(Dispatchers.IO) { repository.saveAggregate(aggregate) }
        relinkFileHashes(aggregate)
        return id
    }

    context(_: RaiseContext)
    private suspend fun relinkFileHashes(aggregate: NewMetadataAggregate) {
        aggregate.editions
            .mapNotNull { it.edition.fileHash }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { hash -> relinkPort.relinkByFileHash(hash) }
    }

    context(_: RaiseContext)
    override suspend fun deleteByBookId(bookId: BookId) {
        withContext(Dispatchers.IO) { repository.deleteMetadataByBookId(bookId) }
    }
}
