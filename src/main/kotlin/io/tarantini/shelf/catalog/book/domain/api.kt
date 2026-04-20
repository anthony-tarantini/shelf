package io.tarantini.shelf.catalog.book.domain

import arrow.core.raise.context.ensure
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.metadata.domain.ASIN
import io.tarantini.shelf.catalog.metadata.domain.ISBN10
import io.tarantini.shelf.catalog.metadata.domain.ISBN13
import io.tarantini.shelf.processing.import.domain.StagedEditionMetadata
import io.tarantini.shelf.processing.import.domain.StagedSeries
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.user.activity.domain.BookUserState
import kotlinx.serialization.Serializable

@Serializable
data class BookSummary(
    val id: BookId,
    val title: String,
    val coverPath: StoragePath? = null,
    val authorNames: List<String> = emptyList(),
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val userState: BookUserState? = null,
)

@Serializable
data class BookPage(
    val items: List<SavedBookAggregate>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
)

@Serializable
data class UpdateBookMetadataRequest(
    val title: String? = null,
    val description: String? = null,
    val authors: List<String>? = null,
    val selectedAuthorIds: Map<String, String?>? = null,
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
fun UpdateBookMetadataRequest.toCommand(): UpdateBookMetadataCommand {
    ensure(
        selectedAuthorIds == null || authors?.toSet()?.containsAll(selectedAuthorIds.keys) == true
    ) {
        UnknownSelectedAuthorMapping
    }
    val selectedIds = selectedAuthorIds?.values?.filterNotNull().orEmpty()
    ensure(selectedIds.distinct().size == selectedIds.size) { DuplicateSelectedAuthorIdMapping }

    val relinkAuthors =
        authors?.map { rawName ->
            val selectedRaw = selectedAuthorIds?.get(rawName)
            if (selectedRaw != null) {
                AuthorRelinkIntent.UseExisting(AuthorId(selectedRaw))
            } else {
                AuthorRelinkIntent.UpsertByName(AuthorName(rawName))
            }
        }

    val canonicalAuthorNames =
        relinkAuthors
            ?.map { authorIntent ->
                when (authorIntent) {
                    is AuthorRelinkIntent.UseExisting -> null
                    is AuthorRelinkIntent.UpsertByName -> authorIntent.name.value
                }
            }
            ?.filterNotNull()
            ?.map(::canonicalizeBookRelationName) ?: emptyList()
    ensure(canonicalAuthorNames.distinct().size == canonicalAuthorNames.size) {
        DuplicateBookAuthors
    }

    val relinkSeries =
        series?.map { SeriesRelinkIntent.AuthorScopedUpsertByName(SeriesName(it.name), it.index) }

    ensure(relinkSeries == null || !relinkAuthors.isNullOrEmpty()) { SeriesRequiresAuthors }

    val canonicalSeriesNames =
        relinkSeries?.map { canonicalizeBookRelationName(it.name.value) } ?: emptyList()
    ensure(canonicalSeriesNames.distinct().size == canonicalSeriesNames.size) {
        DuplicateBookSeries
    }

    fun mapEdition(metadata: StagedEditionMetadata?): EditionIdentifiersCommand? {
        if (metadata == null) return null
        return EditionIdentifiersCommand(
            isbn10 = metadata.isbn10?.let { ISBN10(it) },
            isbn13 = metadata.isbn13?.let { ISBN13(it) },
            asin = metadata.asin?.let { ASIN(it) },
            narrator = metadata.narrator?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    val mappedGenres = genres?.map { Genre(it) } ?: emptyList()
    val canonicalGenres = mappedGenres.map { canonicalizeBookRelationName(it.value) }
    ensure(canonicalGenres.distinct().size == canonicalGenres.size) { DuplicateBookGenres }

    val mappedMoods = moods?.map { Mood(it) } ?: emptyList()
    val canonicalMoods = mappedMoods.map { canonicalizeBookRelationName(it.value) }
    ensure(canonicalMoods.distinct().size == canonicalMoods.size) { DuplicateBookMoods }

    return UpdateBookMetadataCommand(
        title = title?.let { BookTitle(it) },
        description = description,
        authors = relinkAuthors,
        publisher = publisher?.let { PublisherName(it) },
        publishYear = publishYear?.let { PublishYear(it) },
        genres = mappedGenres,
        moods = mappedMoods,
        series = relinkSeries,
        ebookMetadata = mapEdition(ebookMetadata),
        audiobookMetadata = mapEdition(audiobookMetadata),
        coverUrl = coverUrl?.let { CoverSourceUrl(it) },
    )
}
