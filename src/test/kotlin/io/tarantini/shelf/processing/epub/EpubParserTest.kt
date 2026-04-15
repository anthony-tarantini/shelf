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
import java.nio.file.Paths
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class EpubParserTest :
    StringSpec({
        val parser = epubParser()
        val epubPath = Paths.get("src/test/resources/book.epub")

        "parse should extract metadata from epub" {
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
    })
