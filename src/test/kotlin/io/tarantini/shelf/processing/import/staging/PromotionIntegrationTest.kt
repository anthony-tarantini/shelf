@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import.staging

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.processing.import.domain.StagedSeries
import io.tarantini.shelf.processing.import.domain.promoteStagedBook
import io.tarantini.shelf.testing.MediaFixtureFactory
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.auth.JwtToken
import io.tarantini.shelf.user.identity.domain.UserId
import java.nio.file.Files
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PromotionIntegrationTest :
    IntegrationSpec({
        "should import to staging and then promote to catalog" {
            testWithDeps { deps ->
                val userId = UserId.fromRaw(Uuid.random())
                val auth = JwtContext(JwtToken("fake"), userId)
                val epubPath = Files.createTempFile("shelf-promotion", ".epub")
                MediaFixtureFactory.createMinimalEpub(
                    epubPath,
                    MediaFixtureFactory.EpubSpec(
                        title = "The Primal Hunter 14: A LitRPG Adventure",
                        author = "Zogarth",
                        seriesName = "The Primal Hunter",
                        seriesIndex = 14.0,
                    ),
                )

                recover({
                    // 1. Import to staging
                    val stagedBook =
                        with(this) {
                            with(auth) { deps.importService.importToStaging(epubPath, "test.epub") }
                        }
                    stagedBook.title shouldBe "The Primal Hunter 14: A LitRPG Adventure"
                    stagedBook.series shouldBe
                        listOf(StagedSeries(name = "The Primal Hunter", index = 14.0))

                    // Verify it's in staging
                    val stagedList = with(this) { with(auth) { deps.stagedBookService.getAll() } }
                    stagedList.items.any { it.id == stagedBook.id } shouldBe true

                    // 2. Promote to catalog
                    with(this) {
                        with(auth) {
                            with(deps.database) {
                                deps.stagedBookService.promote(promoteStagedBook(stagedBook.id))
                            }
                        }
                    }

                    // 3. Verify in catalog
                    val books = deps.database.bookQueries.selectAll().executeAsList()
                    val promotedBook = books.find { it.title == stagedBook.title }
                    promotedBook shouldNotBe null

                    // Verify author was created and linked
                    val authors = deps.database.authorQueries.selectAll().executeAsList()
                    authors.shouldNotBeEmpty()

                    // Verify it's gone from staging
                    val stagedListAfter =
                        with(this) { with(auth) { deps.stagedBookService.getAll() } }
                    stagedListAfter.items.any { it.id == stagedBook.id } shouldBe false
                }) {
                    fail("Promotion failed: $it")
                }
            }
        }
    })
