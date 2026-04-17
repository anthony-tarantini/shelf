@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import.staging

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.processing.import.domain.StagedEditionMetadata
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.auth.JwtToken
import io.tarantini.shelf.user.identity.domain.UserId
import java.nio.file.Paths
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MergeIntegrationTest :
    IntegrationSpec({
        "should merge a staged book into an existing book" {
            testWithDeps { deps ->
                val userId = UserId.fromRaw(Uuid.random())
                val auth = JwtContext(JwtToken("fake"), userId)
                val epubPath = Paths.get("src/test/resources/book.epub")

                recover({
                    // 1. Import and promote Book A (EBOOK)
                    val stagedA =
                        with(this) {
                            with(auth) {
                                deps.importService.importToStaging(epubPath, "bookA.epub")
                            }
                        }

                    with(this) {
                        with(auth) {
                            with(deps.database) { deps.stagedBookService.promote(stagedA.id) }
                        }
                    }

                    val bookA =
                        deps.database.bookQueries.selectAll().executeAsList().first {
                            it.title == stagedA.title
                        }

                    // 2. Import Book B
                    val stagedB =
                        with(this) {
                            with(auth) {
                                deps.importService.importToStaging(epubPath, "bookB.epub")
                            }
                        }

                    // Change Book B to be an AUDIOBOOK so they don't conflict on format
                    // and give it a different path
                    with(this) {
                        with(auth) {
                            deps.stagedBookService.update(
                                stagedId = stagedB.id,
                                audiobookMetadata =
                                    StagedEditionMetadata(storagePath = "different/path.m4b"),
                            )
                        }
                    }

                    // 3. Merge Book B into Book A
                    with(this) {
                        with(auth) {
                            with(deps.database) {
                                deps.stagedBookService.merge(stagedB.id, bookA.id.value.toString())
                            }
                        }
                    }

                    // 4. Verify results
                    val editions =
                        deps.database.metadataQueries
                            .selectEditionsByBookId(bookA.id)
                            .executeAsList()
                    editions.size shouldBe 2
                    editions.any { it.format == BookFormat.EBOOK } shouldBe true
                    editions.any { it.format == BookFormat.AUDIOBOOK } shouldBe true

                    // Verify it's gone from staging
                    val stagedList = with(this) { with(auth) { deps.stagedBookService.getAll() } }
                    stagedList.items.any { it.id == stagedB.id } shouldBe false
                }) {
                    fail("Merge failed: $it")
                }
            }
        }
    })
