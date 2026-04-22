@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.sanitization

import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

class SanitizationPersistenceTest :
    IntegrationSpec({
        fun unique(prefix: String) = "$prefix-${System.nanoTime()}"

        suspend fun createAudiobookEdition(
            deps: io.tarantini.shelf.app.Dependencies
        ): Pair<
            io.tarantini.shelf.catalog.book.domain.BookId,
            io.tarantini.shelf.catalog.metadata.domain.EditionId,
        > {
            val bookId = deps.database.bookQueries.insert(unique("san-book"), null).executeAsOne()
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
                        size = 1024,
                    )
                    .executeAsOne()
            return bookId to editionId
        }

        "sanitization_jobs should enforce one active job per edition" {
            testWithDeps { deps ->
                val (bookId, editionId) = createAudiobookEdition(deps)
                val originalPathA = StoragePath.fromRaw("test/${unique("original-a")}.m4b")
                val originalPathB = StoragePath.fromRaw("test/${unique("original-b")}.m4b")

                val firstJob =
                    deps.database.sanitizationQueries
                        .insert(
                            bookId = bookId,
                            editionId = editionId,
                            originalPath = originalPathA,
                        )
                        .executeAsOne()

                var secondInsertFailed = false
                try {
                    deps.database.sanitizationQueries
                        .insert(
                            bookId = bookId,
                            editionId = editionId,
                            originalPath = originalPathB,
                        )
                        .executeAsOne()
                } catch (_: Throwable) {
                    secondInsertFailed = true
                }
                secondInsertFailed shouldBe true

                deps.database.sanitizationQueries.updateStatus(
                    status = "APPROVED",
                    errorMessage = null,
                    id = firstJob,
                )

                val nextJob =
                    deps.database.sanitizationQueries
                        .insert(
                            bookId = bookId,
                            editionId = editionId,
                            originalPath = originalPathB,
                        )
                        .executeAsOne()

                (nextJob == firstJob) shouldBe false
                deps.database.sanitizationQueries
                    .selectByEditionId(editionId)
                    .executeAsList()
                    .size shouldBe 2
            }
        }
    })
