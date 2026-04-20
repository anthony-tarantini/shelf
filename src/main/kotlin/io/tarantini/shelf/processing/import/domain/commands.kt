package io.tarantini.shelf.processing.import.domain

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.book.domain.BookId

data class ScanDirectoryCommand(val path: String)

data class MergeStagedBookCommand(val targetBookId: BookId)

data class PromoteStagedBookCommand(val stagedId: String)

data class StagedBatchCommand(
    val ids: List<String> = emptyList(),
    val action: StagedBatchAction,
    val author: String? = null,
)

data class UpdateStagedBookCommand(
    val title: String? = null,
    val description: String? = null,
    val authors: List<String>? = null,
    val selectedAuthorIds: Map<String, AuthorId?>? = null,
    val publisher: String? = null,
    val publishYear: Int? = null,
    val genres: List<String>? = null,
    val moods: List<String>? = null,
    val series: List<StagedSeries>? = null,
    val ebookMetadata: StagedEditionMetadata? = null,
    val audiobookMetadata: StagedEditionMetadata? = null,
    val coverUrl: String? = null,
)

context(_: RaiseContext)
fun ScanDirectoryRequest.toCommand(): ScanDirectoryCommand {
    val normalized = path.trim()
    ensure(normalized.isNotEmpty()) { DirectoryNotFound }
    return ScanDirectoryCommand(path = normalized)
}

context(_: RaiseContext)
fun MergeStagedBookRequest.toCommand(): MergeStagedBookCommand =
    MergeStagedBookCommand(targetBookId = BookId(targetBookId))

context(_: RaiseContext)
fun UpdateStagedBookRequest.toCommand(): UpdateStagedBookCommand =
    UpdateStagedBookCommand(
        title = title,
        description = description,
        authors = authors,
        selectedAuthorIds = selectedAuthorIds?.mapValues { (_, id) -> id?.let { AuthorId(it) } },
        publisher = publisher,
        publishYear = publishYear,
        genres = genres,
        moods = moods,
        series = series,
        ebookMetadata = ebookMetadata,
        audiobookMetadata = audiobookMetadata,
        coverUrl = coverUrl,
    )

context(_: RaiseContext)
fun StagedBatchRequest.toCommand(): StagedBatchCommand =
    StagedBatchCommand(
        ids = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        action = action,
        author = author?.trim()?.takeIf { it.isNotEmpty() },
    )

context(_: RaiseContext)
fun promoteStagedBook(stagedId: String): PromoteStagedBookCommand =
    PromoteStagedBookCommand(stagedId = stagedId.trim())
