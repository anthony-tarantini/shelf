@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import.staging

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.processing.import.domain.*
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.auth.JwtToken
import io.tarantini.shelf.user.identity.domain.UserId
import java.nio.file.Paths
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay

class StagedBatchTest :
    IntegrationSpec({
        "should filter by author" {
            testWithDeps { deps ->
                val userId = UserId.fromRaw(Uuid.random())
                val auth = JwtContext(JwtToken("fake"), userId)
                val epubPath = Paths.get("src/test/resources/book.epub")

                recover({
                    // 1. Import to staging
                    with(this) {
                        with(auth) { deps.importService.importToStaging(epubPath, "test.epub") }
                    }

                    // 2. Filter by existing author "Zogarth" (case-insensitive "zog")
                    val filtered =
                        with(this) { with(auth) { deps.stagedBookService.getAll(author = "zog") } }
                    filtered.items.size shouldBe 1
                    filtered.totalCount shouldBe 1

                    // 3. Filter by non-existing author
                    val empty =
                        with(this) {
                            with(auth) { deps.stagedBookService.getAll(author = "NonExistent") }
                        }
                    empty.items.size shouldBe 0
                    empty.totalCount shouldBe 0
                }) {
                    fail("Filtering failed: $it")
                }
            }
        }

        "should promote all matching authors" {
            testWithDeps { deps ->
                val userId = UserId.fromRaw(Uuid.random())
                val auth = JwtContext(JwtToken("fake"), userId)
                val epubPath = Paths.get("src/test/resources/book.epub")

                recover({
                    // 1. Import to staging
                    with(this) {
                        with(auth) { deps.importService.importToStaging(epubPath, "test.epub") }
                    }

                    // 2. Batch promote all with filter "zog"
                    with(this) {
                        with(auth) {
                            with(deps.database) {
                                deps.stagedBookService.batch(
                                    StagedBatchRequest(
                                        action = StagedBatchAction.PROMOTE_ALL,
                                        author = "zog",
                                    )
                                )
                            }
                        }
                    }

                    // 3. Wait for the async batch worker to finish
                    var progress: BatchProgress?
                    var attempts = 0
                    do {
                        progress =
                            with(this) { with(auth) { deps.stagedBookService.getBatchProgress() } }
                        if (progress?.status == BatchStatus.COMPLETED) break
                        delay(20)
                        attempts++
                    } while (attempts < 100)

                    progress?.status shouldBe BatchStatus.COMPLETED

                    // 4. Verify promoted
                    val books = deps.database.bookQueries.selectAll().executeAsList()
                    books.any { it.title.contains("Primal Hunter") } shouldBe true

                    // 5. Verify staging empty once the batch is done
                    val staged = with(this) { with(auth) { deps.stagedBookService.getAll() } }
                    staged.items.size shouldBe 0
                }) {
                    fail("Promote all failed: $it")
                }
            }
        }
    })
