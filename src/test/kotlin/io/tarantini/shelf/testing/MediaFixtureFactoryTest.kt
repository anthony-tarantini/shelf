package io.tarantini.shelf.testing

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.util.zip.ZipFile

private fun commandAvailable(command: String): Boolean =
    runCatching {
            val process = ProcessBuilder(command, "-version").redirectErrorStream(true).start()
            try {
                process.waitFor() == 0
            } finally {
                process.destroy()
            }
        }
        .getOrDefault(false)

class MediaFixtureFactoryTest :
    StringSpec({
        "createMinimalEpub writes expected core entries" {
            val epub = Files.createTempFile("fixture-epub", ".epub")
            MediaFixtureFactory.createMinimalEpub(
                epub,
                MediaFixtureFactory.EpubSpec(
                    title = "Fixture Book",
                    author = "Fixture Author",
                    seriesName = "Fixture Series",
                    seriesIndex = 2.0,
                    includeCover = true,
                ),
            )

            ZipFile(epub.toFile()).use { zip ->
                (zip.getEntry("mimetype") != null) shouldBe true
                (zip.getEntry("META-INF/container.xml") != null) shouldBe true
                (zip.getEntry("OEBPS/content.opf") != null) shouldBe true
                (zip.getEntry("OEBPS/images/cover.png") != null) shouldBe true
                val opf =
                    zip.getInputStream(zip.getEntry("OEBPS/content.opf"))
                        .readBytes()
                        .decodeToString()
                opf.contains("Fixture Book") shouldBe true
                opf.contains("Fixture Author") shouldBe true
                opf.contains("belongs-to-collection") shouldBe true
            }
        }

        "createLibationManifest and createBinaryFile write fixture files" {
            val root = Files.createTempDirectory("fixture-libation")
            val audio = root.resolve("audio/episode.m4b")
            val manifest = root.resolve("episode.json")

            MediaFixtureFactory.createBinaryFile(audio, byteArrayOf(1, 2, 3, 4))
            MediaFixtureFactory.createLibationManifest(
                manifest,
                """
                { "asin": "B012345678", "title": "Fixture Episode", "audioFile": "audio/episode.m4b" }
                """,
            )

            Files.exists(audio) shouldBe true
            Files.size(audio) shouldBe 4L
            Files.exists(manifest) shouldBe true
            Files.readString(manifest).contains("Fixture Episode") shouldBe true
        }

        "createTaggedMp3 creates an audio file when ffmpeg is available" {
            if (commandAvailable("ffmpeg") && commandAvailable("ffprobe")) {
                val mp3 = Files.createTempFile("fixture-audio", ".mp3")
                MediaFixtureFactory.createTaggedMp3(
                    mp3,
                    MediaFixtureFactory.AudioSpec(
                        title = "Fixture Audio",
                        artist = "Fixture Artist",
                        album = "Fixture Album",
                        genre = "Fiction",
                        date = "2025-01-01",
                        comment = "Fixture Comment",
                    ),
                )

                Files.exists(mp3) shouldBe true
                (Files.size(mp3) > 0L) shouldBe true
            }
        }
    })
