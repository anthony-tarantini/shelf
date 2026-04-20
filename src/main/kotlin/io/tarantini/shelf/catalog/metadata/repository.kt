@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.metadata

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.*
import io.tarantini.shelf.catalog.metadata.persistence.*
import kotlin.uuid.ExperimentalUuidApi

interface MetadataRepository {
    context(_: RaiseContext)
    fun getMetadataByBookId(bookId: BookId): SavedMetadataRoot

    context(_: RaiseContext)
    fun getMetadataForBooks(bookIds: List<BookId>): Map<BookId, SavedMetadataAggregate>

    context(_: RaiseContext)
    fun getEditionsByBookId(bookId: BookId): List<SavedEdition>

    context(_: RaiseContext)
    fun selectEditionByFileHash(fileHash: String): SavedEdition?

    context(_: RaiseContext)
    fun getChaptersByEditionId(editionId: EditionId): List<SavedChapter>

    context(_: RaiseContext)
    fun getBookIdsByFormat(format: BookFormat): List<BookId>

    context(_: RaiseContext)
    fun saveAggregate(aggregate: NewMetadataAggregate): MetadataId

    context(_: RaiseContext)
    fun saveMetadata(metadata: NewMetadataRoot): MetadataId

    context(_: RaiseContext)
    fun updateEditionIdentifiers(
        isbn10: ISBN10?,
        isbn13: ISBN13?,
        asin: ASIN?,
        narrator: String?,
        bookId: BookId,
        format: BookFormat,
    )

    context(_: RaiseContext)
    fun deleteMetadataByBookId(bookId: BookId)
}

fun metadataRepository(metadataQueries: MetadataQueries): MetadataRepository =
    SqlDelightMetadataRepository(metadataQueries)

private class SqlDelightMetadataRepository(private val metadataQueries: MetadataQueries) :
    MetadataRepository {
    context(_: RaiseContext)
    override fun getMetadataByBookId(bookId: BookId): SavedMetadataRoot =
        metadataQueries.getMetadataByBookId(bookId)

    context(_: RaiseContext)
    override fun getMetadataForBooks(bookIds: List<BookId>): Map<BookId, SavedMetadataAggregate> =
        metadataQueries.getMetadataForBooks(bookIds)

    context(_: RaiseContext)
    override fun getEditionsByBookId(bookId: BookId): List<SavedEdition> =
        metadataQueries.getEditionsByBookId(bookId)

    context(_: RaiseContext)
    override fun selectEditionByFileHash(fileHash: String): SavedEdition? =
        metadataQueries.selectEditionByFileHash(fileHash).executeAsOneOrNull()?.toDomain()

    context(_: RaiseContext)
    override fun getChaptersByEditionId(editionId: EditionId): List<SavedChapter> =
        metadataQueries.getChaptersByEditionId(editionId)

    context(_: RaiseContext)
    override fun getBookIdsByFormat(format: BookFormat): List<BookId> =
        metadataQueries.getBookIdsByFormat(format)

    context(_: RaiseContext)
    override fun saveAggregate(aggregate: NewMetadataAggregate): MetadataId =
        metadataQueries.saveAggregate(aggregate)

    context(_: RaiseContext)
    override fun saveMetadata(metadata: NewMetadataRoot): MetadataId =
        metadataQueries.saveMetadata(metadata)

    context(_: RaiseContext)
    override fun updateEditionIdentifiers(
        isbn10: ISBN10?,
        isbn13: ISBN13?,
        asin: ASIN?,
        narrator: String?,
        bookId: BookId,
        format: BookFormat,
    ) {
        metadataQueries.updateEditionIdentifiers(isbn10, isbn13, asin, narrator, bookId, format)
    }

    context(_: RaiseContext)
    override fun deleteMetadataByBookId(bookId: BookId) {
        metadataQueries.deleteMetadataByBookId(bookId)
    }
}
