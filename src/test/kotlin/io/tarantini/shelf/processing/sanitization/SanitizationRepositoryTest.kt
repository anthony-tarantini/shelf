@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.sanitization

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.processing.sanitization.domain.AdSegment
import io.tarantini.shelf.processing.sanitization.domain.AudioTimestamp
import io.tarantini.shelf.processing.sanitization.domain.DetectedSegment
import io.tarantini.shelf.processing.sanitization.domain.SanitizationJob
import io.tarantini.shelf.processing.sanitization.domain.SanitizationResult
import io.tarantini.shelf.processing.sanitization.domain.SanitizationStatus
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

class SanitizationRepositoryTest :
    IntegrationSpec({
        fun unique(prefix: String) = "$prefix-${System.nanoTime()}"

        "sanitization repository should create and update job lifecycle" {
            testWithDeps { deps ->
                val repo = sanitizationMutationRepository(deps.database.sanitizationQueries)
                val bookId = deps.database.bookQueries.insert(unique("book"), null).executeAsOne()
                val editionId =
                    deps.database.metadataQueries
                        .insertEdition(
                            bookId = bookId,
                            format = BookFormat.AUDIOBOOK,
                            path = StoragePath.fromRaw("test/${unique("edition")}.m4b"),
                            fileHash = null,
                            narrator = null,
                            translator = null,
                            isbn10 = null,
                            isbn13 = null,
                            asin = null,
                            pages = null,
                            totalTime = 100.0,
                            size = 1024,
                        )
                        .executeAsOne()

                recover({
                    val created =
                        repo.createJob(
                            SanitizationJob.new(
                                bookId = bookId,
                                editionId = editionId,
                                originalPath = StoragePath.fromRaw("test/${unique("orig")}.m4b"),
                            )
                        )
                    created.status shouldBe SanitizationStatus.PENDING

                    val processing =
                        repo.updateStatus(
                            id = created.id.id,
                            status = SanitizationStatus.PROCESSING,
                        )
                    processing.status shouldBe SanitizationStatus.PROCESSING

                    val reviewed =
                        repo.updateResult(
                            id = created.id.id,
                            result =
                                SanitizationResult(
                                    sanitizedPath =
                                        StoragePath.fromRaw("test/${unique("san")}.m4b"),
                                    transcriptPath =
                                        StoragePath.fromRaw("test/${unique("tr")}.vtt"),
                                    detectedSegments =
                                        listOf(
                                            DetectedSegment(
                                                segment =
                                                    AdSegment(
                                                        start = AudioTimestamp(5.0),
                                                        end = AudioTimestamp(12.0),
                                                        confidence = 0.95,
                                                    ),
                                                label = "ad",
                                            )
                                        ),
                                    totalRemovedSeconds = 7.0,
                                ),
                        )
                    reviewed.status shouldBe SanitizationStatus.REVIEW
                    reviewed.detectedSegments.size shouldBe 1

                    repo.getPendingJobs(limit = 10).any { it.id.id == created.id.id } shouldBe false
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
