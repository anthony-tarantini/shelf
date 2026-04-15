@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.audiobook

import arrow.core.raise.recover
import arrow.fx.coroutines.resourceScope
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
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
        lateinit var tempAudioFile: Path

        beforeSpec {
            if (!commandAvailable("ffmpeg") || !commandAvailable("ffprobe")) {
                throw TestAbortedException("ffmpeg/ffprobe not available in test environment")
            }

            tempAudioFile = Files.createTempFile("shelf-test-audio", ".mp3")
            // Generate a 1-second silent MP3 with metadata using ffmpeg
            val process =
                ProcessBuilder(
                        "ffmpeg",
                        "-y",
                        "-f",
                        "lavfi",
                        "-i",
                        "anullsrc=r=44100:cl=mono",
                        "-t",
                        "1",
                        "-metadata",
                        "title=Test Audiobook",
                        "-metadata",
                        "artist=Test Author",
                        "-metadata",
                        "album=Test Series",
                        "-metadata",
                        "genre=Science Fiction",
                        "-metadata",
                        "date=2024-01-01",
                        "-metadata",
                        "comment=Test Description",
                        tempAudioFile.absolutePathString(),
                    )
                    .start()
            process.waitFor()
        }

        afterSpec { tempAudioFile.deleteIfExists() }

        "parse should extract metadata from mp3" {
            resourceScope {
                recover({
                    val bookId = BookId.fromRaw(Uuid.random())
                    val (metadata, coverPath) =
                        parser.parse(
                            this@resourceScope,
                            tempAudioFile,
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
    })
