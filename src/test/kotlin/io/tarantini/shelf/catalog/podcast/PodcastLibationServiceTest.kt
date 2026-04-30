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
import io.tarantini.shelf.testing.MediaFixtureFactory
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.uuid.ExperimentalUuidApi

class PodcastLibationServiceTest :
    IntegrationSpec({
        "libation importer persists run summary and skips on rerun" {
            testWithDeps { deps ->
                val dropDir = Files.createTempDirectory("libation-import-drop")
                MediaFixtureFactory.createBinaryFile(
                    dropDir.resolve("audio/book-one.m4b"),
                    byteArrayOf(1, 2, 3, 4),
                )
                MediaFixtureFactory.createLibationManifest(
                    dropDir.resolve("book-one.json"),
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
                    """,
                )

                val service =
                    podcastLibationService(
                        enabled = true,
                        dropDirectory = dropDir.toString(),
                        scanner = LibationScanner(LibationManifestParser()),
                        libationImportQueries = deps.database.libationImportQueries,
                        seriesQueries = deps.database.seriesQueries,
                        podcastQueries = deps.database.podcastQueries,
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

        "libation importer reuses podcast across runs when ASIN changes but series title matches" {
            testWithDeps { deps ->
                val dropDir = Files.createTempDirectory("libation-import-series-key")
                MediaFixtureFactory.createBinaryFile(
                    dropDir.resolve("audio/episode-one.m4b"),
                    byteArrayOf(1, 2, 3, 4),
                )
                MediaFixtureFactory.createBinaryFile(
                    dropDir.resolve("audio/episode-two.m4b"),
                    byteArrayOf(5, 6, 7, 8),
                )

                val manifestPath = dropDir.resolve("episode.json")
                manifestPath.writeText(
                    """
                    {
                      "asin": "B111111111",
                      "title": "Episode One",
                      "seriesTitle": "Libation: The Show!",
                      "audioFile": "audio/episode-one.m4b"
                    }
                    """
                )

                val service =
                    podcastLibationService(
                        enabled = true,
                        dropDirectory = dropDir.toString(),
                        scanner = LibationScanner(LibationManifestParser()),
                        libationImportQueries = deps.database.libationImportQueries,
                        seriesQueries = deps.database.seriesQueries,
                        podcastQueries = deps.database.podcastQueries,
                        storageService = deps.storageService,
                    )

                val first =
                    recover({ service.scanNow() }) { fail("First scan should succeed: $it") }
                first.importedCreatedCount shouldBe 1

                manifestPath.writeText(
                    """
                    {
                      "asin": "B222222222",
                      "title": "Episode Two",
                      "seriesTitle": "libation the show",
                      "audioFile": "audio/episode-two.m4b"
                    }
                    """
                )

                val second =
                    recover({ service.scanNow() }) { fail("Second scan should succeed: $it") }
                second.importedCreatedCount shouldBe 1

                val targetPodcasts =
                    deps.database.podcastQueries.selectAll().executeAsList().filter {
                        it.series_title == "Libation: The Show!"
                    }
                targetPodcasts.size shouldBe 1
                targetPodcasts.first().episode_count shouldBe 2
            }
        }

        "libation scan counts invalid manifest when audio file is missing" {
            testWithDeps { deps ->
                val dropDir = Files.createTempDirectory("libation-import-missing-audio")
                MediaFixtureFactory.createLibationManifest(
                    dropDir.resolve("missing-audio.json"),
                    """
                    {
                      "asin": "B999999999",
                      "title": "Broken Episode",
                      "seriesTitle": "Broken Series",
                      "audioFile": "audio/does-not-exist.m4b"
                    }
                    """,
                )

                val service =
                    podcastLibationService(
                        enabled = true,
                        dropDirectory = dropDir.toString(),
                        scanner = LibationScanner(LibationManifestParser()),
                        libationImportQueries = deps.database.libationImportQueries,
                        seriesQueries = deps.database.seriesQueries,
                        podcastQueries = deps.database.podcastQueries,
                        storageService = deps.storageService,
                    )

                val scan = recover({ service.scanNow() }) { fail("Scan should succeed: $it") }
                scan.discoveredCount shouldBe 1
                scan.validManifestCount shouldBe 0
                scan.invalidManifestCount shouldBe 1
                scan.importedCreatedCount shouldBe 0
                scan.importedSkippedCount shouldBe 0
                scan.importedFailedCount shouldBe 0

                val latestRun =
                    deps.database.libationImportQueries.selectLatestRun().executeAsOneOrNull()
                latestRun.shouldNotBeNull()
                latestRun.status shouldBe "COMPLETED"
                latestRun.invalid_manifest_count shouldBe 1
            }
        }

        "libation importer skips duplicate ASIN in same scan run" {
            testWithDeps { deps ->
                val dropDir = Files.createTempDirectory("libation-import-duplicate-asin")
                MediaFixtureFactory.createBinaryFile(
                    dropDir.resolve("audio/episode-one.m4b"),
                    byteArrayOf(1, 2, 3, 4),
                )
                MediaFixtureFactory.createBinaryFile(
                    dropDir.resolve("audio/episode-two.m4b"),
                    byteArrayOf(5, 6, 7, 8),
                )
                MediaFixtureFactory.createLibationManifest(
                    dropDir.resolve("episode-one.json"),
                    """
                    {
                      "asin": "B333333333",
                      "title": "Episode One",
                      "seriesTitle": "Duplicate Series",
                      "audioFile": "audio/episode-one.m4b"
                    }
                    """,
                )
                MediaFixtureFactory.createLibationManifest(
                    dropDir.resolve("episode-two.json"),
                    """
                    {
                      "asin": "B333333333",
                      "title": "Episode Two Duplicate ASIN",
                      "seriesTitle": "Duplicate Series",
                      "audioFile": "audio/episode-two.m4b"
                    }
                    """,
                )

                val service =
                    podcastLibationService(
                        enabled = true,
                        dropDirectory = dropDir.toString(),
                        scanner = LibationScanner(LibationManifestParser()),
                        libationImportQueries = deps.database.libationImportQueries,
                        seriesQueries = deps.database.seriesQueries,
                        podcastQueries = deps.database.podcastQueries,
                        storageService = deps.storageService,
                    )

                val scan = recover({ service.scanNow() }) { fail("Scan should succeed: $it") }
                scan.validManifestCount shouldBe 2
                scan.importedCreatedCount shouldBe 1
                scan.importedSkippedCount shouldBe 1
                scan.importedFailedCount shouldBe 0

                val duplicateSeriesPodcasts =
                    deps.database.podcastQueries.selectAll().executeAsList().filter {
                        it.series_title == "Duplicate Series"
                    }
                duplicateSeriesPodcasts.size shouldBe 1
                duplicateSeriesPodcasts.first().episode_count shouldBe 1
            }
        }
    })
