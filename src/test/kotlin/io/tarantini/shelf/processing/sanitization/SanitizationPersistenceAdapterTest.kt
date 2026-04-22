@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.sanitization

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.processing.sanitization.domain.AdSegment
import io.tarantini.shelf.processing.sanitization.domain.AudioTimestamp
import io.tarantini.shelf.processing.sanitization.domain.DetectedSegment
import io.tarantini.shelf.processing.sanitization.domain.SanitizationJob
import io.tarantini.shelf.processing.sanitization.domain.SanitizationResult
import io.tarantini.shelf.processing.sanitization.domain.SanitizationStatus
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

class SanitizationPersistenceAdapterTest :
    IntegrationSpec({
        fun unique(prefix: String) = "$prefix-${System.nanoTime()}"

        suspend fun createEdition(
            deps: io.tarantini.shelf.app.Dependencies
        ): Pair<BookId, EditionId> {
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
                        totalTime = 120.0,
                        size = 1000,
                    )
                    .executeAsOne()
            return bookId to editionId
        }

        "createJob and getJobByEditionId should round-trip and parse result segments" {
            testWithDeps { deps ->
                val (bookId, editionId) = createEdition(deps)

                recover({
                    val newJob =
                        SanitizationJob.new(
                            bookId = bookId,
                            editionId = editionId,
                            originalPath = StoragePath.fromRaw("test/${unique("original")}.m4b"),
                        )
                    val jobId = deps.database.sanitizationQueries.createJob(newJob)
                    deps.database.sanitizationQueries.updateJobStatus(
                        id = jobId,
                        status = SanitizationStatus.PROCESSING,
                    )

                    val segments =
                        listOf(
                            DetectedSegment(
                                segment =
                                    AdSegment(
                                        start = AudioTimestamp(10.0),
                                        end = AudioTimestamp(25.0),
                                        confidence = 0.9,
                                    ),
                                label = "sponsor",
                            )
                        )
                    deps.database.sanitizationQueries.updateJobResult(
                        id = jobId,
                        result =
                            SanitizationResult(
                                sanitizedPath = StoragePath.fromRaw("test/${unique("san")}.m4b"),
                                transcriptPath =
                                    StoragePath.fromRaw("test/${unique("transcript")}.vtt"),
                                detectedSegments = segments,
                                totalRemovedSeconds = 15.0,
                            ),
                    )

                    val persisted = deps.database.sanitizationQueries.getJobByEditionId(editionId)
                    persisted.id.id shouldBe jobId
                    persisted.status shouldBe SanitizationStatus.REVIEW
                    persisted.detectedSegments.size shouldBe 1
                    persisted.totalRemovedSeconds shouldBe 15.0
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
