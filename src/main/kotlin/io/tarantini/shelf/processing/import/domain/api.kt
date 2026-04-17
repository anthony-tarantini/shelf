package io.tarantini.shelf.processing.import.domain

import kotlinx.serialization.Serializable

@Serializable
data class UpdateStagedBookRequest(
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

@Serializable
data class StagedBookPage(
    val items: List<StagedBook>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
)

@Serializable
enum class StagedBatchAction {
    PROMOTE,
    DELETE,
    PROMOTE_ALL,
}

@Serializable
data class StagedBatchRequest(
    val ids: List<String> = emptyList(),
    val action: StagedBatchAction,
    val author: String? = null,
)

@Serializable data class ScanDirectoryRequest(val path: String, val staged: Boolean = true)

@Serializable data class MergeStagedBookRequest(val targetBookId: String)
