@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.import.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.MediaType
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private fun stagedBook(
    title: String = "Test Book",
    authors: List<String> = listOf("Author One"),
    mediaType: MediaType = MediaType.EBOOK,
    storagePath: String = "staged/author/book.epub",
    genres: List<String> = emptyList(),
    moods: List<String> = emptyList(),
    description: String? = null,
    publisher: String? = null,
    publishYear: Int? = null,
    series: List<StagedSeries> = emptyList(),
    ebookMetadata: StagedEditionMetadata? = null,
    audiobookMetadata: StagedEditionMetadata? = null,
    chapters: List<io.tarantini.shelf.catalog.metadata.domain.NewChapter> = emptyList(),
    size: Long = 1024L,
) =
    StagedBook(
        id = Uuid.random().toString(),
        userId = UserId.fromRaw(Uuid.random()),
        title = title,
        authors = authors,
        storagePath = storagePath,
        genres = genres,
        moods = moods,
        description = description,
        publisher = publisher,
        publishYear = publishYear,
        series = series,
        ebookMetadata = ebookMetadata,
        audiobookMetadata = audiobookMetadata,
        mediaType = mediaType,
        chapters = chapters,
        size = size,
    )

class StagedBookDeciderTest :
    StringSpec({

        // --- applyUpdate ---

        "applyUpdate applies title when provided" {
            val book = stagedBook(title = "Old Title")
            val cmd = UpdateStagedBookCommand(title = "New Title")

            val result = StagedBookDecider.applyUpdate(book, cmd)

            result.title shouldBe "New Title"
        }

        "applyUpdate skips blank title" {
            val book = stagedBook(title = "Keep This")
            val cmd = UpdateStagedBookCommand(title = "  ")

            val result = StagedBookDecider.applyUpdate(book, cmd)

            result.title shouldBe "Keep This"
        }

        "applyUpdate applies multiple fields" {
            val book = stagedBook()
            val cmd =
                UpdateStagedBookCommand(
                    description = "New desc",
                    publisher = "Tor Books",
                    publishYear = 2024,
                    genres = listOf("Fantasy"),
                    moods = listOf("Epic"),
                )

            val result = StagedBookDecider.applyUpdate(book, cmd)

            result.description shouldBe "New desc"
            result.publisher shouldBe "Tor Books"
            result.publishYear shouldBe 2024
            result.genres shouldBe listOf("Fantasy")
            result.moods shouldBe listOf("Epic")
        }

        "applyUpdate no-op when all fields null" {
            val book = stagedBook(title = "Original", description = "Desc")
            val cmd = UpdateStagedBookCommand()

            val result = StagedBookDecider.applyUpdate(book, cmd)

            result shouldBe book
        }

        // --- planPromotion ---

        "planPromotion creates ebook edition for EBOOK mediaType" {
            val book = stagedBook(mediaType = MediaType.EBOOK)
            val bookId = BookId.fromRaw(Uuid.random())

            val plan = StagedBookDecider.planPromotion(book, bookId)

            plan.metadata.editions shouldHaveSize 1
            plan.metadata.editions[0].edition.format shouldBe BookFormat.EBOOK
            plan.warnings.shouldBeEmpty()
        }

        "planPromotion creates audiobook edition for AUDIOBOOK mediaType" {
            val book = stagedBook(mediaType = MediaType.AUDIOBOOK)
            val bookId = BookId.fromRaw(Uuid.random())

            val plan = StagedBookDecider.planPromotion(book, bookId)

            plan.metadata.editions shouldHaveSize 1
            plan.metadata.editions[0].edition.format shouldBe BookFormat.AUDIOBOOK
            plan.warnings.shouldBeEmpty()
        }

        "planPromotion creates both editions when both metadata present" {
            val book =
                stagedBook(
                    mediaType = MediaType.EBOOK,
                    ebookMetadata = StagedEditionMetadata(storagePath = "staged/book.epub"),
                    audiobookMetadata = StagedEditionMetadata(storagePath = "staged/book.m4b"),
                )
            val bookId = BookId.fromRaw(Uuid.random())

            val plan = StagedBookDecider.planPromotion(book, bookId)

            plan.metadata.editions shouldHaveSize 2
            val formats = plan.metadata.editions.map { it.edition.format }.toSet()
            formats shouldBe setOf(BookFormat.EBOOK, BookFormat.AUDIOBOOK)
        }

        "planPromotion collects warnings for invalid identifiers" {
            val book =
                stagedBook(
                    mediaType = MediaType.EBOOK,
                    ebookMetadata = StagedEditionMetadata(isbn10 = "not-valid"),
                )
            val bookId = BookId.fromRaw(Uuid.random())

            val plan = StagedBookDecider.planPromotion(book, bookId)

            plan.warnings shouldHaveSize 1
            plan.warnings[0].field shouldBe "ISBN10"
        }

        "planPromotion maps all metadata fields to root" {
            val book =
                stagedBook(
                    title = "My Book",
                    description = "A great book",
                    publisher = "Tor",
                    publishYear = 2023,
                    genres = listOf("Fantasy", "SciFi"),
                    moods = listOf("Dark", "Epic"),
                )
            val bookId = BookId.fromRaw(Uuid.random())

            val plan = StagedBookDecider.planPromotion(book, bookId)

            plan.metadata.metadata.title shouldBe "My Book"
            plan.metadata.metadata.description shouldBe "A great book"
            plan.metadata.metadata.publisher shouldBe "Tor"
            plan.metadata.metadata.published shouldBe 2023
            plan.metadata.metadata.genres shouldBe listOf("Fantasy", "SciFi")
            plan.metadata.metadata.moods shouldBe listOf("Dark", "Epic")
        }
    })
