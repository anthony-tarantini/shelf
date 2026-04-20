@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import.domain

import arrow.core.raise.recover
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.*
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

object StagedBookDecider {

    fun applyUpdate(existing: StagedBook, command: UpdateStagedBookCommand): StagedBook {
        var result = existing
        if (!command.title.isNullOrBlank()) result = result.copy(title = command.title)
        if (command.description != null) result = result.copy(description = command.description)
        if (command.authors != null) result = result.copy(authors = command.authors)
        if (command.selectedAuthorIds != null)
            result = result.copy(selectedAuthorIds = command.selectedAuthorIds)
        if (command.publisher != null) result = result.copy(publisher = command.publisher)
        if (command.publishYear != null) result = result.copy(publishYear = command.publishYear)
        if (command.genres != null) result = result.copy(genres = command.genres)
        if (command.moods != null) result = result.copy(moods = command.moods)
        if (command.series != null) result = result.copy(series = command.series)
        if (command.ebookMetadata != null)
            result = result.copy(ebookMetadata = command.ebookMetadata)
        if (command.audiobookMetadata != null)
            result = result.copy(audiobookMetadata = command.audiobookMetadata)
        return result
    }

    fun planPromotion(stagedBook: StagedBook, bookId: BookId): PromotionPlan {
        val warnings = mutableListOf<WarningDetail>()

        val root =
            NewMetadataRoot(
                id = Identity.Unsaved,
                bookId = bookId,
                title = stagedBook.title,
                description = stagedBook.description,
                publisher = stagedBook.publisher,
                published = stagedBook.publishYear,
                language = null,
                genres = stagedBook.genres,
                moods = stagedBook.moods,
            )

        val editions = mutableListOf<EditionWithChapters<PersistenceState.Unsaved>>()

        if (stagedBook.mediaType == MediaType.EBOOK || stagedBook.ebookMetadata != null) {
            val isPrimary = stagedBook.mediaType == MediaType.EBOOK
            val meta = stagedBook.ebookMetadata
            editions.add(
                buildEdition(
                    bookId = bookId,
                    format = BookFormat.EBOOK,
                    meta = meta,
                    fallbackPath = stagedBook.storagePath,
                    isPrimary = isPrimary,
                    size = if (isPrimary) stagedBook.size else 0L,
                    chapters = if (isPrimary) stagedBook.chapters else emptyList(),
                    warnings = warnings,
                    bookTitle = stagedBook.title,
                )
            )
        }

        if (stagedBook.mediaType == MediaType.AUDIOBOOK || stagedBook.audiobookMetadata != null) {
            val isPrimary = stagedBook.mediaType == MediaType.AUDIOBOOK
            val meta = stagedBook.audiobookMetadata
            editions.add(
                buildEdition(
                    bookId = bookId,
                    format = BookFormat.AUDIOBOOK,
                    meta = meta,
                    fallbackPath = stagedBook.storagePath,
                    isPrimary = isPrimary,
                    size = if (isPrimary) stagedBook.size else 0L,
                    chapters = if (isPrimary) stagedBook.chapters else emptyList(),
                    warnings = warnings,
                    bookTitle = stagedBook.title,
                )
            )
        }

        return PromotionPlan(
            metadata = NewMetadataAggregate(metadata = root, editions = editions),
            warnings = warnings,
        )
    }

    private fun buildEdition(
        bookId: BookId,
        format: BookFormat,
        meta: StagedEditionMetadata?,
        fallbackPath: String,
        isPrimary: Boolean,
        size: Long,
        chapters: List<NewChapter>,
        warnings: MutableList<WarningDetail>,
        bookTitle: String,
    ): EditionWithChapters<PersistenceState.Unsaved> {
        val edition =
            NewEdition(
                id = Identity.Unsaved,
                bookId = bookId,
                format = format,
                fileHash = meta?.fileHash,
                path =
                    meta?.storagePath?.let { StoragePath.fromRaw(it) }
                        ?: StoragePath.fromRaw(fallbackPath),
                narrator = meta?.narrator,
                isbn10 = tryIdentifier(meta?.isbn10, warnings, bookTitle, "ISBN10") { ISBN10(it) },
                isbn13 = tryIdentifier(meta?.isbn13, warnings, bookTitle, "ISBN13") { ISBN13(it) },
                asin = tryIdentifier(meta?.asin, warnings, bookTitle, "ASIN") { ASIN(it) },
                pages = meta?.pages?.toLong(),
                totalTime = meta?.totalTime,
                size = size,
            )
        return EditionWithChapters(edition = edition, chapters = chapters)
    }
}

data class PromotionPlan(val metadata: NewMetadataAggregate, val warnings: List<WarningDetail>)

private inline fun <T> tryIdentifier(
    rawValue: String?,
    warnings: MutableList<WarningDetail>,
    bookTitle: String,
    fieldName: String,
    construct: RaiseContext.(String) -> T,
): T? {
    if (rawValue == null) return null
    return recover({ construct(rawValue) }) { err ->
        warnings.add(
            WarningDetail(fileName = bookTitle, field = fieldName, message = err.toString())
        )
        null
    }
}
