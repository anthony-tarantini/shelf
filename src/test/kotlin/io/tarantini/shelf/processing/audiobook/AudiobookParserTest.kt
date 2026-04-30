@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.audiobook

import arrow.core.raise.recover
import arrow.fx.coroutines.resourceScope
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.testing.MediaFixtureFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.opentest4j.TestAbortedException

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

class AudiobookParserTest :
    StringSpec({
        val parser = audiobookParser()
        val hasAudioTooling = commandAvailable("ffmpeg") && commandAvailable("ffprobe")
        var tempAudioFile: Path? = null

        beforeSpec {
            if (!hasAudioTooling) {
                return@beforeSpec
            }

            tempAudioFile = Files.createTempFile("shelf-test-audio", ".mp3")
            MediaFixtureFactory.createTaggedMp3(
                checkNotNull(tempAudioFile),
                MediaFixtureFactory.AudioSpec(
                    title = "Test Audiobook",
                    artist = "Test Author",
                    album = "Test Series",
                    genre = "Science Fiction",
                    date = "2024-01-01",
                    comment = "Test Description",
                ),
            )
        }

        afterSpec { tempAudioFile?.deleteIfExists() }

        "parse should extract metadata from mp3" {
            if (!hasAudioTooling) {
                throw TestAbortedException("ffmpeg/ffprobe not available in test environment")
            }

            resourceScope {
                recover({
                    val bookId = BookId.fromRaw(Uuid.random())
                    val (metadata, coverPath) =
                        parser.parse(
                            this@resourceScope,
                            checkNotNull(tempAudioFile),
                            "mp3",
                            "test-audiobook.mp3",
                            bookId,
                        )

                    metadata.core.title shouldBe "Test Audiobook"
                    metadata.authors shouldBe listOf("Test Author")
                    metadata.core.published shouldBe 2024
                    metadata.core.description shouldBe "Test Description"
                    metadata.core.genres shouldBe listOf("Science Fiction")
                    metadata.edition.format shouldBe BookFormat.AUDIOBOOK
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "parse should tolerate invalid date and split multi-value tags" {
            if (!hasAudioTooling) {
                throw TestAbortedException("ffmpeg/ffprobe not available in test environment")
            }
            val file = Files.createTempFile("shelf-test-audio-invalid-date", ".mp3")
            MediaFixtureFactory.createTaggedMp3(
                file,
                MediaFixtureFactory.AudioSpec(
                    title = "Edge Audiobook",
                    artist = "Author One/Author Two;Author Three",
                    album = "Edge Series",
                    genre = "Sci-Fi/Fantasy;Adventure",
                    date = "not-a-year",
                    comment = "Edge Description",
                ),
            )

            resourceScope {
                recover({
                    val bookId = BookId.fromRaw(Uuid.random())
                    val (metadata, _) =
                        parser.parse(this@resourceScope, file, "mp3", "edge-audio.mp3", bookId)

                    metadata.core.title shouldBe "Edge Audiobook"
                    metadata.core.published shouldBe null
                    metadata.authors shouldBe listOf("Author One", "Author Two", "Author Three")
                    metadata.core.genres shouldBe listOf("Sci-Fi", "Fantasy", "Adventure")
                    metadata.edition.format shouldBe BookFormat.AUDIOBOOK
                }) {
                    fail("Should not have failed: $it")
                }
            }
            file.deleteIfExists()
        }

        "parse should produce empty authors when artist is blank" {
            if (!hasAudioTooling) {
                throw TestAbortedException("ffmpeg/ffprobe not available in test environment")
            }
            val file = Files.createTempFile("shelf-test-audio-empty-artist", ".mp3")
            MediaFixtureFactory.createTaggedMp3(
                file,
                MediaFixtureFactory.AudioSpec(
                    title = "No Author Audiobook",
                    artist = "   ",
                    album = "No Author Series",
                    genre = "Mystery",
                    date = "2024",
                    comment = "No author metadata",
                ),
            )

            resourceScope {
                recover({
                    val bookId = BookId.fromRaw(Uuid.random())
                    val (metadata, _) =
                        parser.parse(this@resourceScope, file, "mp3", "no-author.mp3", bookId)
                    metadata.authors shouldBe emptyList()
                }) {
                    fail("Should not have failed: $it")
                }
            }
            file.deleteIfExists()
        }
    })
