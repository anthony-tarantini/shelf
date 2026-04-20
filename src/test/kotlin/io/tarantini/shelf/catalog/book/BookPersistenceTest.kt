@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.catalog.author.createAuthor
import io.tarantini.shelf.catalog.series.createSeries
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

class BookPersistenceTest :
    IntegrationSpec({
        "createBook and getBookById" {
            testWithDeps { deps ->
                val queries = deps.database.bookQueries
                recover({
                    with(deps) {
                        val id =
                            queries.createBook(
                                "Foundation",
                                StoragePath.fromRaw("books/asimov/foundation.epub"),
                            )
                        val book = queries.getBookById(id)
                        book.title shouldBe "Foundation"
                        book.coverPath?.value shouldBe "books/asimov/foundation.epub"
                    }
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "linkSeries and getBooksForSeries" {
            testWithDeps { deps ->
                val bookQueries = deps.database.bookQueries
                val seriesQueries = deps.database.seriesQueries

                recover({
                    with(deps) {
                        val bookId = bookQueries.createBook("Foundation", null)
                        val seriesId = seriesQueries.createSeries("Foundation Series")

                        bookQueries.linkSeries(bookId, seriesId, 1.0)

                        val booksInSeries = bookQueries.getBooksForSeries(listOf(seriesId))
                        booksInSeries[seriesId]?.any { it.title == "Foundation" } shouldBe true
                    }
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "link author and getBooksForAuthors" {
            testWithDeps { deps ->
                val bookQueries = deps.database.bookQueries
                val authorQueries = deps.database.authorQueries

                recover({
                    with(deps) {
                        val bookId = bookQueries.createBook("I, Robot", null)
                        val authorId = authorQueries.createAuthor("Isaac Asimov")

                        bookQueries.insertBookAuthor(bookId, authorId)

                        val booksByAuthor = bookQueries.getBooksForAuthors(listOf(authorId))
                        booksByAuthor[authorId]?.any { it.title == "I, Robot" } shouldBe true
                    }
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
