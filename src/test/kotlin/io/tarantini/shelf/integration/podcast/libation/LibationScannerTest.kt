package io.tarantini.shelf.integration.podcast.libation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.testing.MediaFixtureFactory
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class LibationScannerTest :
    StringSpec({
        "scan resolves valid manifests with parsed metadata" {
            val root = Files.createTempDirectory("libation-scan-valid")
            val audioDir = root.resolve("audio").createDirectories()
            val audioPath = audioDir.resolve("book-one.m4b")
            MediaFixtureFactory.createBinaryFile(audioPath, byteArrayOf(1, 2, 3))
            MediaFixtureFactory.createLibationManifest(
                root.resolve("manifest.json"),
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

            val result = LibationScanner(LibationManifestParser()).scan(root)
            result.discoveredCount shouldBe 1
            result.validManifestCount shouldBe 1
            result.invalidManifestCount shouldBe 0
            result.manifests.shouldHaveSize(1)
            val manifest = result.manifests.first()
            manifest.asin shouldBe "B012345678"
            manifest.title shouldBe "Imported From Libation"
            manifest.seriesTitle shouldBe "Libation Series"
            manifest.description shouldBe "desc"
            manifest.publishedYear shouldBe 2023
            manifest.durationSeconds shouldBe 1234.5
        }

        "scan rejects symlink audio paths" {
            val root = Files.createTempDirectory("libation-scan-symlink")
            val outsideRoot = Files.createTempDirectory("libation-outside")
            val outsideAudio = outsideRoot.resolve("outside.m4b")
            Files.write(outsideAudio, byteArrayOf(9, 9, 9))
            Files.createSymbolicLink(root.resolve("audio-link.m4b"), outsideAudio)
            root
                .resolve("manifest.json")
                .writeText(
                    """
                    {
                      "asin": "B123456789",
                      "title": "Linked",
                      "audioFile": "audio-link.m4b"
                    }
                    """
                        .trimIndent()
                )

            val result = LibationScanner(LibationManifestParser()).scan(root)
            result.discoveredCount shouldBe 1
            result.validManifestCount shouldBe 0
            result.invalidManifestCount shouldBe 1
            result.manifests.shouldHaveSize(0)
        }

        "scan supports audio-only exports by inferring metadata from file paths" {
            val root = Files.createTempDirectory("libation-audio-only")
            val seriesDir = root.resolve("Once We Were Spacemen [B0FZD2PWVV]").createDirectories()
            val audioPath =
                seriesDir.resolve(
                    "Once We Were Spacemen with Nathan Fillion & Alan Tudyk | Episode 1 [B0FZV3RCXV].mp3"
                )
            MediaFixtureFactory.createBinaryFile(audioPath, byteArrayOf(4, 5, 6))

            val result = LibationScanner(LibationManifestParser()).scan(root)
            result.discoveredCount shouldBe 1
            result.validManifestCount shouldBe 1
            result.invalidManifestCount shouldBe 0
            result.manifests.shouldHaveSize(1)
            val manifest = result.manifests.first()
            manifest.asin shouldBe "B0FZV3RCXV"
            manifest.title shouldBe
                "Once We Were Spacemen with Nathan Fillion & Alan Tudyk | Episode 1"
            manifest.seriesTitle shouldBe "Once We Were Spacemen"
            manifest.audioPath shouldBe audioPath.toRealPath()
            manifest.description shouldBe null
            manifest.publishedYear shouldBe null
            manifest.durationSeconds shouldBe null
        }

        "scan marks audio-only exports invalid when ASIN cannot be inferred" {
            val root = Files.createTempDirectory("libation-audio-invalid")
            val audioPath = root.resolve("episode-without-asin.mp3")
            MediaFixtureFactory.createBinaryFile(audioPath, byteArrayOf(1, 2, 3))

            val result = LibationScanner(LibationManifestParser()).scan(root)
            result.discoveredCount shouldBe 1
            result.validManifestCount shouldBe 0
            result.invalidManifestCount shouldBe 1
            result.manifests.shouldHaveSize(0)
        }
    })
