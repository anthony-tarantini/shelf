@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import.staging

import arrow.core.raise.either
import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.AccessDenied
import io.tarantini.shelf.processing.storage.FileBytes
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.auth.JwtToken
import io.tarantini.shelf.user.identity.domain.UserId
import java.nio.file.Paths
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class StagedCoverServiceTest :
    IntegrationSpec({
        "preferred staged cover path returns thumbnail when available" {
            testWithDeps { deps ->
                val owner = JwtContext(JwtToken("owner"), UserId.fromRaw(Uuid.random()))
                val epubPath = Paths.get("src/test/resources/book.epub")

                recover({
                    val staged =
                        with(this) {
                            with(owner) {
                                deps.importService.importToStaging(epubPath, "cover.epub")
                            }
                        }
                    staged.coverPath shouldNotBe null
                    val coverPath = StoragePath.fromRaw(staged.coverPath!!)
                    val thumbnailPath = coverPath.thumbnail()

                    with(this) {
                        deps.storageService.save(thumbnailPath, FileBytes(byteArrayOf(1, 2, 3)))
                    }

                    val result =
                        with(this) {
                            with(owner) {
                                either { deps.stagedBookService.getPreferredCoverPath(staged.id) }
                            }
                        }

                    result.fold({ fail("Service call failed: $it") }, { it }) shouldBe thumbnailPath
                }) {
                    fail("Test failed: $it")
                }
            }
        }

        "preferred staged cover path enforces ownership" {
            testWithDeps { deps ->
                val owner = JwtContext(JwtToken("owner"), UserId.fromRaw(Uuid.random()))
                val attacker = JwtContext(JwtToken("attacker"), UserId.fromRaw(Uuid.random()))
                val epubPath = Paths.get("src/test/resources/book.epub")

                recover({
                    val staged =
                        with(this) {
                            with(owner) {
                                deps.importService.importToStaging(epubPath, "owned.epub")
                            }
                        }

                    val result =
                        with(this) {
                            with(attacker) {
                                either { deps.stagedBookService.getPreferredCoverPath(staged.id) }
                            }
                        }

                    result.fold({ it shouldBe AccessDenied }, { fail("Should have been denied") })
                }) {
                    fail("Test failed: $it")
                }
            }
        }
    })
