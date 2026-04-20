@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.Uuid

class BookMetadataDeciderTest :
    StringSpec({
        "decide uses existing title and cover when command does not override" {
            val snapshot =
                BookMetadataSnapshot(
                    book =
                        BookRoot.fromRaw(
                            id = BookId.fromRaw(Uuid.random()),
                            title = "Existing",
                            coverPath = StoragePath.fromRaw("books/1/cover.jpg"),
                        )
                )
            val command = UpdateBookMetadataCommand(description = "desc")

            val decision =
                DefaultBookMetadataDecider.decide(
                    snapshot = snapshot,
                    command = command,
                    resolvedCoverPath = null,
                    syncMetadataToFiles = false,
                )

            decision.mutation.title shouldBe "Existing"
            decision.mutation.bookRecord shouldBe null
            decision.mutation.relationships shouldBe BookRelationshipsMutation.KeepExisting
            decision.events shouldBe emptyList()
        }

        "decide sets mutation update flag when title or cover changes" {
            val snapshot =
                BookMetadataSnapshot(
                    book = BookRoot.fromRaw(BookId.fromRaw(Uuid.random()), "Old", null)
                )
            val command = UpdateBookMetadataCommand(title = BookTitle.fromRaw("New"))
            val decision =
                DefaultBookMetadataDecider.decide(
                    snapshot = snapshot,
                    command = command,
                    resolvedCoverPath = StoragePath.fromRaw("books/2/cover.webp"),
                    syncMetadataToFiles = false,
                )

            decision.mutation.title shouldBe "New"
            decision.mutation.bookRecord shouldBe
                BookRecordMutation(
                    title = "New",
                    coverPath = StoragePath.fromRaw("books/2/cover.webp"),
                )
        }

        "decide mirrors existing author/series replacement semantics" {
            val snapshot =
                BookMetadataSnapshot(
                    book = BookRoot.fromRaw(BookId.fromRaw(Uuid.random()), "Book", null)
                )
            val commandWithoutAuthors =
                UpdateBookMetadataCommand(
                    series =
                        listOf(
                            SeriesRelinkIntent.AuthorScopedUpsertByName(
                                SeriesName.fromRaw("Series"),
                                1.0,
                            )
                        )
                )
            val withoutAuthorsDecision =
                DefaultBookMetadataDecider.decide(
                    snapshot = snapshot,
                    command = commandWithoutAuthors,
                    resolvedCoverPath = null,
                    syncMetadataToFiles = false,
                )
            withoutAuthorsDecision.mutation.relationships shouldBe
                BookRelationshipsMutation.KeepExisting

            val commandWithAuthorsAndSeries =
                UpdateBookMetadataCommand(
                    authors = listOf(AuthorRelinkIntent.UpsertByName(AuthorName.fromRaw("Author"))),
                    series =
                        listOf(
                            SeriesRelinkIntent.AuthorScopedUpsertByName(
                                SeriesName.fromRaw("Series"),
                                1.0,
                            )
                        ),
                )
            val withAuthorsDecision =
                DefaultBookMetadataDecider.decide(
                    snapshot = snapshot,
                    command = commandWithAuthorsAndSeries,
                    resolvedCoverPath = null,
                    syncMetadataToFiles = false,
                )
            withAuthorsDecision.mutation.relationships shouldBe
                BookRelationshipsMutation.Replace(
                    authors = listOf(AuthorRelinkIntent.UpsertByName(AuthorName.fromRaw("Author"))),
                    series =
                        BookSeriesMutation.Replace(
                            listOf(
                                SeriesRelinkIntent.AuthorScopedUpsertByName(
                                    SeriesName.fromRaw("Series"),
                                    1.0,
                                )
                            )
                        ),
                )
        }

        "decide emits sync event when enabled" {
            val bookId = BookId.fromRaw(Uuid.random())
            val snapshot =
                BookMetadataSnapshot(
                    book = BookRoot.fromRaw(id = bookId, title = "Book", coverPath = null)
                )
            val command = UpdateBookMetadataCommand(description = "Updated")
            val decision =
                DefaultBookMetadataDecider.decide(
                    snapshot = snapshot,
                    command = command,
                    resolvedCoverPath = null,
                    syncMetadataToFiles = true,
                )

            decision.events shouldBe listOf(BookDomainEvent.MetadataSyncRequested(bookId))
        }
    })
