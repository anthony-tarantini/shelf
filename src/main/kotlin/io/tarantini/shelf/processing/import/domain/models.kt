package io.tarantini.shelf.processing.import.domain

import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.domain.SavedAuthorRoot
import io.tarantini.shelf.catalog.metadata.domain.MediaType
import io.tarantini.shelf.catalog.metadata.domain.NewChapter
import io.tarantini.shelf.user.identity.domain.UserId
import java.nio.file.Path
import kotlinx.serialization.Serializable

@Serializable
data class ImportJob(
    val userId: UserId,
    val sourcePath: Path,
    val fileName: String,
    val staged: Boolean,
    val deleteSource: Boolean,
    val scanRunId: String? = null,
)

@Serializable
enum class ImportScanStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED,
}

@Serializable data class FailedFileDetail(val fileName: String, val errorMessage: String)

@Serializable data class WarningDetail(val fileName: String, val field: String, val message: String)

@Serializable
data class ImportScanProgress(
    val runId: String,
    val status: ImportScanStatus,
    val sourcePath: String,
    val totalFiles: Int,
    val queuedFiles: Int,
    val completedFiles: Int,
    val failedFiles: Int,
    val failedFileDetails: List<FailedFileDetail> = emptyList(),
    val startedAt: String,
    val finishedAt: String? = null,
)

@Serializable
enum class BatchStatus {
    RUNNING,
    COMPLETED,
    FAILED,
}

@Serializable
data class BatchProgress(
    val runId: String,
    val status: BatchStatus,
    val action: StagedBatchAction,
    val totalItems: Int,
    val completedItems: Int,
    val failedItems: Int,
    val failedItemDetails: List<FailedFileDetail> = emptyList(),
    val warningItems: Int = 0,
    val warningDetails: List<WarningDetail> = emptyList(),
    val startedAt: String,
    val finishedAt: String? = null,
)

@Serializable data class StagedSeries(val name: String, val index: Double?)

@Serializable
data class StagedEditionMetadata(
    val storagePath: String? = null,
    val isbn10: String? = null,
    val isbn13: String? = null,
    val asin: String? = null,
    val narrator: String? = null,
    val pages: Int? = null,
    val totalTime: Double? = null,
)

@Serializable
data class StagedBook(
    val id: String,
    val userId: UserId,
    val title: String,
    val authors: List<String>,
    val authorSuggestions: Map<String, List<SavedAuthorRoot>> = emptyMap(),
    val selectedAuthorIds: Map<String, AuthorId?> = emptyMap(),
    val storagePath: String,
    val coverPath: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    val publishYear: Int? = null,
    val genres: List<String>,
    val moods: List<String> = emptyList(),
    val series: List<StagedSeries> = emptyList(),
    val ebookMetadata: StagedEditionMetadata? = null,
    val audiobookMetadata: StagedEditionMetadata? = null,
    val mediaType: MediaType = MediaType.EBOOK,
    val chapters: List<NewChapter> = emptyList(),
    val size: Long = 0,
    val createdAt: String = "",
)
