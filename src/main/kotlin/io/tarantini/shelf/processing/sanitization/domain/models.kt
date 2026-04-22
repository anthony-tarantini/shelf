@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.sanitization.domain

import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.ConsistentCopyVisibility
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable

@Serializable
enum class SanitizationStatus {
    PENDING,
    PROCESSING,
    REVIEW,
    APPROVED,
    REJECTED,
    FAILED,
    SKIPPED,
}

@Serializable
@ConsistentCopyVisibility
data class SanitizationJob<S : PersistenceState>
private constructor(
    val id: Identity<S, SanitizationJobId>,
    val bookId: BookId,
    val editionId: EditionId,
    val status: SanitizationStatus,
    val originalPath: StoragePath,
    val sanitizedPath: StoragePath?,
    val transcriptPath: StoragePath?,
    val detectedSegments: List<DetectedSegment>,
    val totalRemovedSeconds: Double?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val errorMessage: String?,
) {
    companion object {
        fun new(bookId: BookId, editionId: EditionId, originalPath: StoragePath) =
            SanitizationJob<PersistenceState.Unsaved>(
                id = Identity.Unsaved,
                bookId = bookId,
                editionId = editionId,
                status = SanitizationStatus.PENDING,
                originalPath = originalPath,
                sanitizedPath = null,
                transcriptPath = null,
                detectedSegments = emptyList(),
                totalRemovedSeconds = null,
                createdAt = null,
                updatedAt = null,
                errorMessage = null,
            )

        fun fromRaw(
            id: SanitizationJobId,
            bookId: BookId,
            editionId: EditionId,
            status: SanitizationStatus,
            originalPath: StoragePath,
            sanitizedPath: StoragePath?,
            transcriptPath: StoragePath?,
            detectedSegments: List<DetectedSegment>,
            totalRemovedSeconds: Double?,
            createdAt: Instant?,
            updatedAt: Instant?,
            errorMessage: String?,
        ) =
            SanitizationJob<PersistenceState.Persisted>(
                id = Identity.Persisted(id),
                bookId = bookId,
                editionId = editionId,
                status = status,
                originalPath = originalPath,
                sanitizedPath = sanitizedPath,
                transcriptPath = transcriptPath,
                detectedSegments = detectedSegments,
                totalRemovedSeconds = totalRemovedSeconds,
                createdAt = createdAt,
                updatedAt = updatedAt,
                errorMessage = errorMessage,
            )
    }
}

typealias SavedSanitizationJob = SanitizationJob<PersistenceState.Persisted>

typealias NewSanitizationJob = SanitizationJob<PersistenceState.Unsaved>

@Serializable data class DetectedSegment(val segment: AdSegment, val label: String)

@Serializable
data class SanitizationResult(
    val sanitizedPath: StoragePath,
    val transcriptPath: StoragePath?,
    val detectedSegments: List<DetectedSegment>,
    val totalRemovedSeconds: Double,
)
