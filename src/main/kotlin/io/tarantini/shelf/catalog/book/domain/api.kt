package io.tarantini.shelf.catalog.book.domain

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
