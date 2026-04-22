@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.sanitization.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.processing.storage.StoragePath
import java.util.UUID
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SanitizationModelsTest :
    StringSpec({
        "SanitizationJob.new should create an Unsaved pending job" {
            val bookId = BookId.fromRaw(Uuid.random())
            val editionId = EditionId.fromRaw(UUID.randomUUID())
            val originalPath = StoragePath.fromRaw("books/test/original.m4b")

            val job =
                SanitizationJob.new(
                    bookId = bookId,
                    editionId = editionId,
                    originalPath = originalPath,
                )

            job.id shouldBe Identity.Unsaved
            job.status shouldBe SanitizationStatus.PENDING
            job.originalPath shouldBe originalPath
            job.detectedSegments shouldBe emptyList()
        }

        "SanitizationJob.fromRaw should create a Persisted job" {
            val id = SanitizationJobId.fromRaw(Uuid.random())
            val bookId = BookId.fromRaw(Uuid.random())
            val editionId = EditionId.fromRaw(UUID.randomUUID())
            val createdAt = Instant.parse("2026-01-01T00:00:00Z")

            val job =
                SanitizationJob.fromRaw(
                    id = id,
                    bookId = bookId,
                    editionId = editionId,
                    status = SanitizationStatus.REVIEW,
                    originalPath = StoragePath.fromRaw("books/test/original.m4b"),
                    sanitizedPath = StoragePath.fromRaw("books/test/sanitized.m4b"),
                    transcriptPath = StoragePath.fromRaw("books/test/transcript.vtt"),
                    detectedSegments = emptyList(),
                    totalRemovedSeconds = 10.0,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    errorMessage = null,
                )

            job.id shouldBe Identity.Persisted(id)
            job.status shouldBe SanitizationStatus.REVIEW
            job.detectedSegments shouldBe emptyList()
            job.totalRemovedSeconds shouldBe 10.0
        }
    })
