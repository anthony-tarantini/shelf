package io.tarantini.shelf.catalog.book.domain

import io.tarantini.shelf.app.id
import io.tarantini.shelf.processing.storage.StoragePath

data class BookMetadataSnapshot(val book: SavedBookRoot)

data class BookRecordMutation(val title: String, val coverPath: StoragePath?)

sealed interface BookSeriesMutation {
    data object KeepExisting : BookSeriesMutation

    data class Replace(val values: List<SeriesRelinkIntent.AuthorScopedUpsertByName>) :
        BookSeriesMutation
}

sealed interface BookRelationshipsMutation {
    data object KeepExisting : BookRelationshipsMutation

    data class Replace(val authors: List<AuthorRelinkIntent>, val series: BookSeriesMutation) :
        BookRelationshipsMutation
}

data class BookMetadataMutation(
    val title: String,
    val bookRecord: BookRecordMutation?,
    val description: String?,
    val publisher: String?,
    val publishYear: Int?,
    val genres: List<String>,
    val moods: List<String>,
    val ebookMetadata: EditionIdentifiersCommand?,
    val audiobookMetadata: EditionIdentifiersCommand?,
    val relationships: BookRelationshipsMutation,
)

data class BookMetadataDecision(
    val mutation: BookMetadataMutation,
    val events: List<BookDomainEvent> = emptyList(),
)

interface BookMetadataDecider {
    fun decide(
        snapshot: BookMetadataSnapshot,
        command: UpdateBookMetadataCommand,
        resolvedCoverPath: StoragePath?,
        syncMetadataToFiles: Boolean,
    ): BookMetadataDecision
}

object DefaultBookMetadataDecider : BookMetadataDecider {
    override fun decide(
        snapshot: BookMetadataSnapshot,
        command: UpdateBookMetadataCommand,
        resolvedCoverPath: StoragePath?,
        syncMetadataToFiles: Boolean,
    ): BookMetadataDecision {
        val existing = snapshot.book
        val effectiveTitle = command.title?.value ?: existing.title
        val effectiveCoverPath = resolvedCoverPath ?: existing.coverPath

        val relationships =
            when (command.authors) {
                null -> BookRelationshipsMutation.KeepExisting
                else ->
                    BookRelationshipsMutation.Replace(
                        authors = command.authors,
                        series =
                            command.series?.let { BookSeriesMutation.Replace(it) }
                                ?: BookSeriesMutation.KeepExisting,
                    )
            }

        val bookRecord =
            if (command.title != null || resolvedCoverPath != null) {
                BookRecordMutation(title = effectiveTitle, coverPath = effectiveCoverPath)
            } else {
                null
            }

        val mutation =
            BookMetadataMutation(
                title = effectiveTitle,
                bookRecord = bookRecord,
                description = command.description,
                publisher = command.publisher?.value,
                publishYear = command.publishYear?.value,
                genres = command.genres.map { it.value },
                moods = command.moods.map { it.value },
                ebookMetadata = command.ebookMetadata,
                audiobookMetadata = command.audiobookMetadata,
                relationships = relationships,
            )

        val events =
            if (syncMetadataToFiles) {
                listOf(BookDomainEvent.MetadataSyncRequested(snapshot.book.id.id))
            } else {
                emptyList()
            }

        return BookMetadataDecision(mutation = mutation, events = events)
    }
}
