package io.tarantini.shelf.catalog.metadata

import arrow.core.getOrElse
import arrow.core.raise.context.ensure
import arrow.core.raise.either
import arrow.fx.coroutines.ResourceScope
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.EditionWithChapters
import io.tarantini.shelf.catalog.metadata.domain.ExternalMetadata
import io.tarantini.shelf.catalog.metadata.domain.MetadataAggregate
import io.tarantini.shelf.catalog.metadata.domain.MetadataId
import io.tarantini.shelf.catalog.metadata.domain.MetadataNotFound
import io.tarantini.shelf.catalog.metadata.domain.NewMetadataAggregate
import io.tarantini.shelf.catalog.metadata.domain.SavedMetadataAggregate
import io.tarantini.shelf.catalog.metadata.persistence.MetadataQueries
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
    suspend fun getBookIdsByFormat(
        format: io.tarantini.shelf.catalog.metadata.domain.BookFormat
    ): List<BookId>

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

fun metadataService(
    externalMetadataProvider: ExternalMetadataProvider,
    metadataProcessor: MetadataProcessor,
    metadataQueries: MetadataQueries,
) =
    object : MetadataService {
        context(_: RaiseContext)
        override suspend fun getMetadataForBook(bookId: BookId): SavedMetadataAggregate? =
            withContext(Dispatchers.IO) {
                metadataQueries.transactionWithResult {
                    either {
                            val metadataRecord = metadataQueries.getMetadataByBookId(bookId)
                            val editions =
                                metadataQueries.getEditionsByBookId(bookId).map { edition ->
                                    EditionWithChapters(
                                        edition = edition,
                                        chapters =
                                            metadataQueries.getChaptersByEditionId(edition.id.id),
                                    )
                                }

                            MetadataAggregate(metadata = metadataRecord, editions = editions)
                        }
                        .getOrElse {
                            ensure(it is MetadataNotFound) { it }
                            null
                        }
                }
            }

        context(_: RaiseContext)
        override suspend fun getMetadataForBooks(
            bookIds: List<BookId>
        ): Map<BookId, SavedMetadataAggregate> =
            withContext(Dispatchers.IO) { metadataQueries.getMetadataForBooks(bookIds) }

        context(_: RaiseContext)
        override suspend fun getBookIdsByFormat(
            format: io.tarantini.shelf.catalog.metadata.domain.BookFormat
        ): List<BookId> = withContext(Dispatchers.IO) { metadataQueries.getBookIdsByFormat(format) }

        context(_: RaiseContext)
        override suspend fun getExternalMetadata(query: String) =
            externalMetadataProvider.searchBookMetadataByName(query)

        context(_: RaiseContext)
        override suspend fun save(metadata: NewMetadataAggregate): MetadataId =
            withContext(Dispatchers.IO) { metadataQueries.saveAggregate(metadata) }

        context(_: RaiseContext)
        override suspend fun processAndSave(
            scope: ResourceScope,
            sourcePath: Path,
            fileName: String,
            bookId: BookId,
        ): MetadataId {
            val processed = metadataProcessor.process(scope, sourcePath, fileName, bookId)
            val aggregate =
                NewMetadataAggregate(
                    metadata = processed.metadata,
                    editions =
                        listOf(
                            EditionWithChapters(
                                edition = processed.edition,
                                chapters = processed.chapters,
                            )
                        ),
                )
            return withContext(Dispatchers.IO) { metadataQueries.saveAggregate(aggregate) }
        }

        context(_: RaiseContext)
        override suspend fun deleteByBookId(bookId: BookId) {
            withContext(Dispatchers.IO) { metadataQueries.deleteMetadataByBookId(bookId) }
        }
    }
