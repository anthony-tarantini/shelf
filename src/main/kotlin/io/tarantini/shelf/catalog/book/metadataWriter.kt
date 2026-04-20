package io.tarantini.shelf.catalog.book

import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.book.domain.BookMetadataMutation
import io.tarantini.shelf.catalog.book.persistence.BookQueries
import io.tarantini.shelf.catalog.metadata.MetadataRepository
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.NewMetadataRoot

internal class SqlDelightBookMetadataWriter(
    private val bookQueries: BookQueries,
    private val metadataRepository: MetadataRepository,
) : BookMetadataWriter {
    context(_: RaiseContext)
    override fun applyBaseMutation(id: BookId, mutation: BookMetadataMutation) {
        mutation.bookRecord?.let { record ->
            bookQueries.update(record.title, record.coverPath, id).executeAsOne()
        }

        metadataRepository.saveMetadata(
            NewMetadataRoot(
                id = io.tarantini.shelf.app.Identity.Unsaved,
                bookId = id,
                title = mutation.title,
                description = mutation.description,
                publisher = mutation.publisher,
                published = mutation.publishYear,
                language = null,
                genres = mutation.genres,
                moods = mutation.moods,
            )
        )

        mutation.ebookMetadata?.let { ebook ->
            metadataRepository.updateEditionIdentifiers(
                isbn10 = ebook.isbn10,
                isbn13 = ebook.isbn13,
                asin = ebook.asin,
                narrator = null,
                bookId = id,
                format = BookFormat.EBOOK,
            )
        }

        mutation.audiobookMetadata?.let { audiobook ->
            metadataRepository.updateEditionIdentifiers(
                isbn10 = audiobook.isbn10,
                isbn13 = audiobook.isbn13,
                asin = audiobook.asin,
                narrator = audiobook.narrator,
                bookId = id,
                format = BookFormat.AUDIOBOOK,
            )
        }
    }
}
