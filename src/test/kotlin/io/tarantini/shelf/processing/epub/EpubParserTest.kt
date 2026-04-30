@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.epub

import arrow.core.raise.recover
import arrow.fx.coroutines.resourceScope
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.ParsedSeries
import io.tarantini.shelf.testing.MediaFixtureFactory
import java.nio.file.Files
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class EpubParserTest :
    StringSpec({
        val parser = epubParser()

        "parse should extract metadata from epub" {
            val epubPath = Files.createTempFile("shelf-test-book", ".epub")
            MediaFixtureFactory.createMinimalEpub(
                epubPath,
                MediaFixtureFactory.EpubSpec(
                    title = "The Primal Hunter 14: A LitRPG Adventure",
                    author = "Zogarth",
                    seriesName = "The Primal Hunter",
                    seriesIndex = 14.0,
                ),
            )
            resourceScope {
                recover({
                    val bookId = BookId.fromRaw(Uuid.random())
                    val (metadata, coverPath) = parser.parse(this@resourceScope, epubPath, bookId)

                    metadata.core.title shouldBe "The Primal Hunter 14: A LitRPG Adventure"
                    metadata.authors shouldBe listOf("Zogarth")
                    metadata.edition.format shouldBe BookFormat.EBOOK
                    metadata.series shouldBe
                        listOf(ParsedSeries(name = "The Primal Hunter", index = 14.0))
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "parse should return empty series when epub has no series metadata" {
            val epubPath = Files.createTempFile("shelf-test-book-no-series", ".epub")
            MediaFixtureFactory.createMinimalEpub(
                epubPath,
                MediaFixtureFactory.EpubSpec(
                    title = "Standalone Book",
                    author = "Single Author",
                    seriesName = null,
                    seriesIndex = null,
                ),
            )
            resourceScope {
                recover({
                    val bookId = BookId.fromRaw(Uuid.random())
                    val (metadata, _) = parser.parse(this@resourceScope, epubPath, bookId)
                    metadata.series shouldBe emptyList()
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "parse should read calibre fallback series metadata" {
            val epubPath = Files.createTempFile("shelf-test-book-calibre-series", ".epub")
            MediaFixtureFactory.createMinimalEpub(
                epubPath,
                MediaFixtureFactory.EpubSpec(
                    title = "Calibre Book",
                    author = "Calibre Author",
                    seriesName = null,
                    seriesIndex = null,
                    calibreSeriesName = "Calibre Series",
                    calibreSeriesIndex = 3.5,
                ),
            )
            resourceScope {
                recover({
                    val bookId = BookId.fromRaw(Uuid.random())
                    val (metadata, _) = parser.parse(this@resourceScope, epubPath, bookId)
                    metadata.series shouldBe
                        listOf(ParsedSeries(name = "Calibre Series", index = 3.5))
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
