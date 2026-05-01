@file:OptIn(ExperimentalUuidApi::class, kotlin.io.path.ExperimentalPathApi::class)

package io.tarantini.shelf.processing.import

import arrow.core.raise.recover
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.processing.import.domain.ScanDirectoryRequest
import io.tarantini.shelf.processing.import.domain.toCommand
import io.tarantini.shelf.user.auth.JwtContext
import io.tarantini.shelf.user.auth.JwtToken
import io.tarantini.shelf.user.identity.domain.UserId
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ImportServiceProgressTest :
    IntegrationSpec({
        "mark empty scans as completed with zero totals" {
            testWithDeps { deps ->
                val userId = UserId.fromRaw(Uuid.random())
                val auth = JwtContext(JwtToken("fake"), userId)
                val scanRoot = Files.createTempDirectory(tempStorageRoot, "scan-progress-empty-")

                try {
                    recover({
                        with(this) {
                            with(auth) {
                                deps.importService.scanDirectory(
                                    ScanDirectoryRequest(path = scanRoot.toString()).toCommand()
                                )
                            }
                        }

                        eventually(5.seconds) {
                            val progress =
                                with(this) { with(auth) { deps.importService.getScanProgress() } }
                            progress?.status?.name shouldBe "COMPLETED"
                            progress?.totalFiles shouldBe 0
                            progress?.completedFiles shouldBe 0
                            progress?.failedFiles shouldBe 0
                            progress?.queuedFiles shouldBe 0
                        }
                    }) { error ->
                        throw AssertionError("Expected empty scan to complete, but got $error")
                    }
                } finally {
                    scanRoot.deleteRecursively()
                }
            }
        }
    })
