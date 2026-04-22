@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.sanitization

import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.processing.sanitization.domain.DetectedSegment
import io.tarantini.shelf.processing.sanitization.domain.NewSanitizationJob
import io.tarantini.shelf.processing.sanitization.domain.SanitizationJob
import io.tarantini.shelf.processing.sanitization.domain.SanitizationJobId
import io.tarantini.shelf.processing.sanitization.domain.SanitizationJobNotFound
import io.tarantini.shelf.processing.sanitization.domain.SanitizationResult
import io.tarantini.shelf.processing.sanitization.domain.SanitizationStatus
import io.tarantini.shelf.processing.sanitization.domain.SavedSanitizationJob
import io.tarantini.shelf.processing.sanitization.persistence.SanitizationQueries
import io.tarantini.shelf.processing.sanitization.persistence.Sanitization_jobs
import java.time.OffsetDateTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val detectedSegmentsJson = Json { ignoreUnknownKeys = true }

context(_: RaiseContext)
fun SanitizationQueries.getJobsByBookId(bookId: BookId): List<SavedSanitizationJob> =
    selectByBookId(bookId).executeAsList().map { it.toDomain() }

context(_: RaiseContext)
fun SanitizationQueries.getJobById(id: SanitizationJobId): SavedSanitizationJob =
    selectById(id).executeAsOneOrNull()?.toDomain() ?: raise(SanitizationJobNotFound)

context(_: RaiseContext)
fun SanitizationQueries.getJobByEditionId(editionId: EditionId): SavedSanitizationJob =
    selectByEditionId(editionId).executeAsList().firstOrNull()?.toDomain()
        ?: raise(SanitizationJobNotFound)

context(_: RaiseContext)
fun SanitizationQueries.getPendingJobs(limit: Long): List<SavedSanitizationJob> =
    selectPending(limit).executeAsList().map { it.toDomain() }

context(_: RaiseContext)
fun SanitizationQueries.createJob(job: NewSanitizationJob): SanitizationJobId =
    insert(bookId = job.bookId, editionId = job.editionId, originalPath = job.originalPath)
        .executeAsOne()

context(_: RaiseContext)
fun SanitizationQueries.updateJobStatus(
    id: SanitizationJobId,
    status: SanitizationStatus,
    errorMessage: String? = null,
) {
    updateStatus(status = status.name, errorMessage = errorMessage, id = id)
}

context(_: RaiseContext)
fun SanitizationQueries.updateJobResult(id: SanitizationJobId, result: SanitizationResult) {
    updateResult(
        sanitizedPath = result.sanitizedPath,
        transcriptPath = result.transcriptPath,
        detectedSegments = detectedSegmentsJson.encodeToString(result.detectedSegments),
        totalRemovedSeconds = result.totalRemovedSeconds,
        id = id,
    )
}

context(_: RaiseContext)
fun SanitizationQueries.deleteJobsByBookId(bookId: BookId) {
    deleteByBookId(bookId)
}

private fun Sanitization_jobs.toDomain() =
    SanitizationJob.fromRaw(
        id = id,
        bookId = book_id,
        editionId = edition_id,
        status = status.toSanitizationStatus(),
        originalPath = original_path,
        sanitizedPath = sanitized_path,
        transcriptPath = transcript_path,
        detectedSegments = decodeSegments(detected_segments),
        totalRemovedSeconds = total_removed_seconds,
        createdAt = created_at.toKotlinInstant(),
        updatedAt = updated_at.toKotlinInstant(),
        errorMessage = error_message,
    )

private fun String.toSanitizationStatus(): SanitizationStatus =
    SanitizationStatus.entries.firstOrNull { it.name == this } ?: SanitizationStatus.FAILED

private fun decodeSegments(raw: String?): List<DetectedSegment> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { detectedSegmentsJson.decodeFromString<List<DetectedSegment>>(raw) }
        .getOrDefault(emptyList())
}

private fun OffsetDateTime?.toKotlinInstant(): Instant? =
    this?.let { Instant.fromEpochMilliseconds(it.toInstant().toEpochMilli()) }
