@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book.domain

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.processing.import.domain.StagedEditionMetadata
import io.tarantini.shelf.processing.import.domain.StagedSeries
import kotlin.uuid.Uuid

class BookMetadataCommandTest :
    StringSpec({
        "toCommand maps valid request to strongly-typed command" {
            recover({
                val selectedAuthorId = Uuid.random().toString()
                val request =
                    UpdateBookMetadataRequest(
                        title = "  Foundation  ",
                        description = "A classic.",
                        authors = listOf(" Isaac Asimov "),
                        selectedAuthorIds = mapOf(" Isaac Asimov " to selectedAuthorId),
                        publisher = "  Gnome Press ",
                        publishYear = 1951,
                        genres = listOf(" Sci-Fi "),
                        moods = listOf(" Epic "),
                        series = listOf(StagedSeries(name = " Foundation ", index = 1.0)),
                        ebookMetadata =
                            StagedEditionMetadata(
                                isbn10 = "0-553-80371-9",
                                isbn13 = "978-0-553-80371-0",
                                asin = "B000FC1PWA",
                            ),
                        coverUrl = "https://hardcover.app/cover.jpg",
                    )

                val command = request.toCommand()
                command.title?.value shouldBe "Foundation"
                (command.authors?.first() as? AuthorRelinkIntent.UseExisting)
                    ?.authorId
                    ?.value
                    ?.toString() shouldBe selectedAuthorId
                command.publisher?.value shouldBe "Gnome Press"
                command.publishYear?.value shouldBe 1951
                command.genres.first().value shouldBe "Sci-Fi"
                command.moods.first().value shouldBe "Epic"
                command.series?.first()?.name?.value shouldBe "Foundation"
                command.ebookMetadata?.isbn10?.value shouldBe "0553803719"
                command.coverUrl?.value shouldBe "https://hardcover.app/cover.jpg"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "toCommand maps author without selected ID to upsert-by-name intent" {
            recover({
                val command =
                    UpdateBookMetadataRequest(authors = listOf(" Ursula Le Guin ")).toCommand()
                (command.authors?.first() as? AuthorRelinkIntent.UpsertByName)?.name?.value shouldBe
                    "Ursula Le Guin"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "toCommand rejects blank author names" {
            recover({
                UpdateBookMetadataRequest(authors = listOf("   ")).toCommand()
                fail("Should have failed")
            }) {
                it shouldBe EmptyBookAuthorName
            }
        }

        "toCommand rejects invalid publish year" {
            recover({
                UpdateBookMetadataRequest(publishYear = 0).toCommand()
                fail("Should have failed")
            }) {
                it shouldBe InvalidBookPublishDate
            }
        }

        "toCommand rejects non-https cover URL" {
            recover({
                UpdateBookMetadataRequest(coverUrl = "http://hardcover.app/cover.jpg").toCommand()
                fail("Should have failed")
            }) {
                it shouldBe InvalidBookCoverUrl
            }
        }

        "toCommand rejects series updates when authors are missing" {
            recover({
                UpdateBookMetadataRequest(series = listOf(StagedSeries(name = "Dune", index = 1.0)))
                    .toCommand()
                fail("Should have failed")
            }) {
                it shouldBe SeriesRequiresAuthors
            }
        }

        "toCommand rejects selectedAuthorIds keys missing from authors list" {
            recover({
                UpdateBookMetadataRequest(
                        authors = listOf("Isaac Asimov"),
                        selectedAuthorIds =
                            mapOf(
                                "Isaac Asimov" to Uuid.random().toString(),
                                "Frank Herbert" to Uuid.random().toString(),
                            ),
                    )
                    .toCommand()
                fail("Should have failed")
            }) {
                it shouldBe UnknownSelectedAuthorMapping
            }
        }

        "toCommand rejects selectedAuthorIds duplicate values across different keys" {
            recover({
                val duplicatedId = Uuid.random().toString()
                UpdateBookMetadataRequest(
                        authors = listOf("Isaac Asimov", "Frank Herbert"),
                        selectedAuthorIds =
                            mapOf("Isaac Asimov" to duplicatedId, "Frank Herbert" to duplicatedId),
                    )
                    .toCommand()
                fail("Should have failed")
            }) {
                it shouldBe DuplicateSelectedAuthorIdMapping
            }
        }

        "toCommand rejects duplicate author names after canonicalization" {
            recover({
                UpdateBookMetadataRequest(authors = listOf(" Frank  Herbert ", "frank herbert"))
                    .toCommand()
                fail("Should have failed")
            }) {
                it shouldBe DuplicateBookAuthors
            }
        }

        "toCommand rejects duplicate series names after canonicalization" {
            recover({
                UpdateBookMetadataRequest(
                        authors = listOf("Frank Herbert"),
                        series =
                            listOf(
                                StagedSeries(name = " Dune ", index = 1.0),
                                StagedSeries(name = "dune", index = 2.0),
                            ),
                    )
                    .toCommand()
                fail("Should have failed")
            }) {
                it shouldBe DuplicateBookSeries
            }
        }

        "toCommand rejects duplicate genres after canonicalization" {
            recover({
                UpdateBookMetadataRequest(genres = listOf(" Sci Fi ", "sci   fi")).toCommand()
                fail("Should have failed")
            }) {
                it shouldBe DuplicateBookGenres
            }
        }

        "toCommand rejects duplicate moods after canonicalization" {
            recover({
                UpdateBookMetadataRequest(moods = listOf(" Space Opera ", "space opera"))
                    .toCommand()
                fail("Should have failed")
            }) {
                it shouldBe DuplicateBookMoods
            }
        }
    })
