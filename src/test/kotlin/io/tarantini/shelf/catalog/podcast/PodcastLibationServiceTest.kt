@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.catalog.podcast.domain.LibationScanStatus
import io.tarantini.shelf.integration.podcast.libation.LibationManifestParser
import io.tarantini.shelf.integration.podcast.libation.LibationScanner
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.uuid.ExperimentalUuidApi

class PodcastLibationServiceTest :
    IntegrationSpec({
        "libation importer persists run summary and skips on rerun" {
            testWithDeps { deps ->
                val dropDir = Files.createTempDirectory("libation-import-drop")
                dropDir.resolve("audio").createDirectories()
                val audioPath = dropDir.resolve("audio/book-one.m4b")
                Files.write(audioPath, byteArrayOf(1, 2, 3, 4))
                dropDir
                    .resolve("book-one.json")
                    .writeText(
                        """
                        {
                          "asin": "B012345678",
                          "title": "Imported From Libation",
                          "seriesTitle": "Libation Series",
                          "description": "desc",
                          "publishedAt": "2023-01-15T12:00:00Z",
                          "durationSeconds": 1234.5,
                          "audioFile": "audio/book-one.m4b"
                        }
                        """
                            .trimIndent()
                    )

                val service =
                    podcastLibationService(
                        enabled = true,
                        dropDirectory = dropDir.toString(),
                        scanner = LibationScanner(LibationManifestParser()),
                        libationImportQueries = deps.database.libationImportQueries,
                        seriesQueries = deps.database.seriesQueries,
                        podcastQueries = deps.database.podcastQueries,
                        bookQueries = deps.database.bookQueries,
                        metadataQueries = deps.database.metadataQueries,
                        storageService = deps.storageService,
                    )

                val first =
                    recover({ service.scanNow() }) { fail("First scan should succeed: $it") }

                first.importedCreatedCount shouldBe 1
                first.importedSkippedCount shouldBe 0
                first.importedFailedCount shouldBe 0
                first.validManifestCount shouldBe 1
                first.invalidManifestCount shouldBe 0
                first.lastRunId.shouldNotBeNull()

                val second =
                    recover({ service.scanNow() }) { fail("Second scan should succeed: $it") }

                second.importedCreatedCount shouldBe 0
                second.importedSkippedCount shouldBe 1
                second.importedFailedCount shouldBe 0
                second.validManifestCount shouldBe 1
                second.invalidManifestCount shouldBe 0

                val latestRun =
                    deps.database.libationImportQueries.selectLatestRun().executeAsOneOrNull()
                latestRun.shouldNotBeNull()
                latestRun.status shouldBe "COMPLETED"
                latestRun.imported_created_count shouldBe 0
                latestRun.imported_skipped_count shouldBe 1
                latestRun.imported_failed_count shouldBe 0

                val record =
                    deps.database.libationImportQueries
                        .selectRecordBySourceKey("libation:b012345678")
                        .executeAsOneOrNull()
                record.shouldNotBeNull()
                record.status shouldBe "IMPORTED"

                val dashboard: LibationScanStatus = service.getStatus()
                dashboard.lastRunId shouldBe latestRun.id.toString()
                dashboard.importedCreatedCount shouldBe 0
                dashboard.importedSkippedCount shouldBe 1
                dashboard.importedFailedCount shouldBe 0
            }
        }
    })
