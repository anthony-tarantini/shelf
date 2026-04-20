@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import arrow.core.raise.either
import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.catalog.author.createAuthor
import io.tarantini.shelf.catalog.author.domain.AuthorId
import io.tarantini.shelf.catalog.author.linkBook
import io.tarantini.shelf.catalog.series.createSeries
import io.tarantini.shelf.catalog.series.domain.SeriesId
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

class BookSummaryProjectionServiceTest :
    IntegrationSpec({
        "author summary projection includes resolved author and series fields" {
            testWithDeps { deps ->
                var authorId: AuthorId? = null
                recover({
                    with(deps.database) {
                        val bookId =
                            bookQueries.createBook(
                                "Projection Book",
                                StoragePath.fromRaw("books/projection-book/cover.jpg"),
                            )
                        val createdAuthorId = authorQueries.createAuthor("Projection Author")
                        val seriesId = seriesQueries.createSeries("Projection Series")
                        authorQueries.linkBook(createdAuthorId, bookId)
                        bookQueries.linkSeries(bookId, seriesId, 7.0)
                        seriesQueries.insertSeriesAuthor(seriesId, createdAuthorId)
                        authorId = createdAuthorId
                    }
                }) {
                    fail("Seeding failed: $it")
                }

                val result = either {
                    deps.bookService.getBookSummariesForAuthor(
                        authorId = authorId ?: error("Missing author id")
                    )
                }

                val summaries = result.fold({ fail("Service call failed: $it") }, { it })
                summaries.shouldHaveSize(1)
                summaries.first().title shouldBe "Projection Book"
                summaries.first().authorNames shouldBe listOf("Projection Author")
                summaries.first().seriesName shouldBe "Projection Series"
                summaries.first().seriesIndex shouldBe 7.0
            }
        }

        "series summary projection includes linked books" {
            testWithDeps { deps ->
                var seriesId: SeriesId? = null
                recover({
                    with(deps.database) {
                        val bookId =
                            bookQueries.createBook(
                                "Series Projection Book",
                                StoragePath.fromRaw("books/series-projection/cover.jpg"),
                            )
                        val createdSeriesId = seriesQueries.createSeries("Series Projection")
                        bookQueries.linkSeries(bookId, createdSeriesId, 1.0)
                        seriesId = createdSeriesId
                    }
                }) {
                    fail("Seeding failed: $it")
                }

                val result = either {
                    deps.bookService.getBookSummariesForSeries(
                        seriesId = seriesId ?: error("Missing series id")
                    )
                }

                val summaries = result.fold({ fail("Service call failed: $it") }, { it })
                summaries.shouldHaveSize(1)
                summaries.first().title shouldBe "Series Projection Book"
            }
        }
    })
