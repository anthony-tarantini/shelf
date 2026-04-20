@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.metadata

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.Chapter
import io.tarantini.shelf.catalog.metadata.domain.ChapterId
import io.tarantini.shelf.catalog.metadata.domain.Edition
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.catalog.metadata.domain.EditionWithChapters
import io.tarantini.shelf.catalog.metadata.domain.MetadataAggregate
import io.tarantini.shelf.catalog.metadata.domain.MetadataId
import io.tarantini.shelf.catalog.metadata.domain.MetadataNotFound
import io.tarantini.shelf.catalog.metadata.domain.MetadataRoot
import io.tarantini.shelf.catalog.metadata.domain.NewChapter
import io.tarantini.shelf.catalog.metadata.domain.NewEdition
import io.tarantini.shelf.catalog.metadata.domain.NewMetadataAggregate
import io.tarantini.shelf.catalog.metadata.domain.NewMetadataRoot
import io.tarantini.shelf.catalog.metadata.domain.SavedChapter
import io.tarantini.shelf.catalog.metadata.domain.SavedEdition
import io.tarantini.shelf.catalog.metadata.domain.SavedMetadataAggregate
import io.tarantini.shelf.catalog.metadata.domain.SavedMetadataRoot
import io.tarantini.shelf.catalog.metadata.persistence.Chapters
import io.tarantini.shelf.catalog.metadata.persistence.Editions
import io.tarantini.shelf.catalog.metadata.persistence.MetadataQueries
import io.tarantini.shelf.catalog.metadata.persistence.SelectMetadataByBookId
import io.tarantini.shelf.catalog.metadata.persistence.SelectMetadataByBookIds
import kotlin.uuid.ExperimentalUuidApi

context(_: RaiseContext)
fun MetadataQueries.getMetadataByBookId(bookId: BookId): SavedMetadataRoot =
    selectMetadataByBookId(bookId).executeAsOneOrNull()?.toDomain() ?: raise(MetadataNotFound)

context(_: RaiseContext)
fun MetadataQueries.getMetadataForBooks(
    bookIds: List<BookId>
): Map<BookId, SavedMetadataAggregate> {
    if (bookIds.isEmpty()) return emptyMap()

    val metadataRoots = selectMetadataByBookIds(bookIds).executeAsList().map { it.toDomain() }
    val editions = selectEditionsByBookIds(bookIds).executeAsList().map { it.toDomain() }
    val editionIds = editions.map { it.id.id }
    val chapters =
        if (editionIds.isNotEmpty()) {
            selectChaptersByEditionIds(editionIds).executeAsList().map { it.toDomain() }
        } else emptyList()

    val chaptersByEditionId = chapters.groupBy { it.editionId.id }
    val editionsWithChaptersByBookId =
        editions
            .map { edition ->
                EditionWithChapters(
                    edition = edition,
                    chapters = chaptersByEditionId[edition.id.id] ?: emptyList(),
                )
            }
            .groupBy { it.edition.bookId }

    return metadataRoots.associateBy(
        { it.bookId },
        { root ->
            MetadataAggregate(
                metadata = root,
                editions = editionsWithChaptersByBookId[root.bookId] ?: emptyList(),
            )
        },
    )
}

context(_: RaiseContext)
fun MetadataQueries.getEditionsByBookId(bookId: BookId): List<SavedEdition> =
    selectEditionsByBookId(bookId).executeAsList().map { it.toDomain() }

context(_: RaiseContext)
fun MetadataQueries.getChaptersByEditionId(editionId: EditionId): List<SavedChapter> =
    selectChaptersByEditionId(editionId).executeAsList().map { it.toDomain() }

context(_: RaiseContext)
fun MetadataQueries.getBookIdsByFormat(format: BookFormat): List<BookId> =
    selectBookIdsByFormat(format).executeAsList()

context(_: RaiseContext)
fun MetadataQueries.saveAggregate(aggregate: NewMetadataAggregate): MetadataId =
    transactionWithResult {
        val metadataId = saveMetadata(aggregate.metadata)

        aggregate.editions.forEach { editionWithChapters ->
            val editionId = saveEdition(editionWithChapters.edition)

            editionWithChapters.chapters.forEach { chapter -> saveChapter(chapter, editionId) }
        }

        metadataId
    }

context(_: RaiseContext)
fun MetadataQueries.saveMetadata(metadata: NewMetadataRoot) = transactionWithResult {
    deleteGenresByBookId(metadata.bookId)
    metadata.genres.forEach { genre -> insertGenre(genre) }
    metadata.genres.forEach { genre -> insertBookGenre(metadata.bookId, genre) }

    deleteMoodsByBookId(metadata.bookId)
    metadata.moods.forEach { mood -> insertMood(mood) }
    metadata.moods.forEach { mood -> insertBookMood(metadata.bookId, mood) }

    metadata.publisher?.let { insertPublisher(metadata.publisher) }

    insertMetadata(
            bookId = metadata.bookId,
            title = metadata.title,
            description = metadata.description,
            publisher = metadata.publisher,
            published = metadata.published,
            language = metadata.language,
        )
        .executeAsOne()
}

context(_: RaiseContext)
fun MetadataQueries.saveEdition(edition: NewEdition): EditionId = transactionWithResult {
    val id =
        insertEdition(
                bookId = edition.bookId,
                format = edition.format,
                path = edition.path,
                fileHash = edition.fileHash,
                narrator = edition.narrator,
                translator = edition.translator,
                isbn10 = edition.isbn10,
                isbn13 = edition.isbn13,
                asin = edition.asin,
                pages = edition.pages,
                totalTime = edition.totalTime,
                size = edition.size,
            )
            .executeAsOne()

    deleteChaptersByEditionId(id)
    id
}

context(_: RaiseContext)
fun MetadataQueries.saveChapter(chapter: NewChapter, editionId: EditionId): ChapterId {
    return insertChapter(
            editionId = editionId,
            title = chapter.title,
            startTime = chapter.startTime,
            endTime = chapter.endTime,
            index = chapter.index?.toLong(),
        )
        .executeAsOne()
}

private fun SelectMetadataByBookId.toDomain() =
    MetadataRoot(
        id = Identity.Persisted(id),
        bookId = book_id,
        title = title,
        description = description,
        publisher = publisher,
        published = published,
        language = language,
        genres = genres.filterNotNull(),
        moods = moods.filterNotNull(),
    )

private fun SelectMetadataByBookIds.toDomain() =
    MetadataRoot(
        id = Identity.Persisted(id),
        bookId = book_id,
        title = title,
        description = description,
        publisher = publisher,
        published = published,
        language = language,
        genres = genres.filterNotNull(),
        moods = moods.filterNotNull(),
    )

internal fun Editions.toDomain() =
    Edition(
        id = Identity.Persisted(id),
        book_id,
        format,
        path,
        file_hash,
        narrator,
        translator,
        isbn_10,
        isbn_13,
        asin,
        pages,
        total_time,
        size,
    )

internal fun Chapters.toDomain() =
    Chapter(
        id = Identity.Persisted(id),
        editionId = Identity.Persisted(edition_id),
        title = title,
        startTime = start_time,
        endTime = end_time,
        index = index_?.toInt(),
    )
