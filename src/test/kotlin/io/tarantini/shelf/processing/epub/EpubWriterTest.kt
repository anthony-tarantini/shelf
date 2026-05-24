package io.tarantini.shelf.processing.epub

import arrow.core.raise.recover
import arrow.fx.coroutines.resourceScope
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.testing.MediaFixtureFactory
import java.nio.file.Files
import java.util.zip.ZipFile

class EpubWriterTest :
    StringSpec({
        "updateMetadata replaces embedded cover image when cover entry exists" {
            val epub = Files.createTempFile("shelf-epub-writer", ".epub")
            val replacementCover = Files.createTempFile("shelf-epub-cover", ".png")
            try {
                MediaFixtureFactory.createMinimalEpub(
                    epub,
                    MediaFixtureFactory.EpubSpec(
                        title = "Original",
                        author = "Author",
                        includeCover = true,
                    ),
                )
                val replacementBytes =
                    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x42, 0x24)
                Files.write(replacementCover, replacementBytes)

                val writer = epubWriter()
                resourceScope {
                    recover({
                        with(this) {
                            writer.updateMetadata(
                                epub,
                                EpubMetadataUpdates(coverImagePath = replacementCover),
                            )
                        }
                    }) { error ->
                        throw AssertionError("Expected EPUB metadata update to succeed: $error")
                    }
                }

                ZipFile(epub.toFile()).use { zip ->
                    val coverEntry = zip.getEntry("OEBPS/images/cover.png")
                    val coverBytes = zip.getInputStream(coverEntry).use { it.readAllBytes() }
                    coverBytes shouldBe replacementBytes
                }
            } finally {
                Files.deleteIfExists(epub)
                Files.deleteIfExists(replacementCover)
            }
        }
    })
