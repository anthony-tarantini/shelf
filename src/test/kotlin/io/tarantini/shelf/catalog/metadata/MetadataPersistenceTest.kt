@file:OptIn(ExperimentalUuidApi::class, ExperimentalContextParameters::class)

package io.tarantini.shelf.catalog.metadata

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.app.PersistenceState
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.metadata.domain.*
import io.tarantini.shelf.processing.storage.StoragePath
import kotlin.uuid.ExperimentalUuidApi

class MetadataPersistenceTest :
    IntegrationSpec({
        "saveAggregate and getMetadataByBookId should persist and retrieve a complete metadata aggregate" {
            testWithDeps { deps ->
                val metadataQueries = deps.database.metadataQueries
                val bookQueries = deps.database.bookQueries

                recover({
                    val bookId = bookQueries.insert("Metadata Test Book", null).executeAsOne()

                    val metadataRoot =
                        MetadataRoot<PersistenceState.Unsaved>(
                            id = Identity.Unsaved,
                            bookId = bookId,
                            title = "Metadata Test Book",
                            description = "Test Description",
                            publisher = "Test Publisher",
                            published = 2024,
                            language = null,
                            genres = listOf("Sci-Fi", "Adventure"),
                            moods = listOf("Intriguing"),
                        )

                    val storagePath = StoragePath.fromRaw("books/test.epub")
                    val edition =
                        Edition<PersistenceState.Unsaved>(
                            id = Identity.Unsaved,
                            bookId = bookId,
                            format = BookFormat.EBOOK,
                            path = storagePath,
                            size = 1024,
                            isbn13 = ISBN13.fromRaw("9780553293357"),
                        )

                    val chapters =
                        listOf(
                            Chapter<PersistenceState.Unsaved>(
                                id = Identity.Unsaved,
                                editionId = Identity.Unsaved,
                                title = "Chapter 1",
                                startTime = 0.0,
                                index = 1,
                            )
                        )

                    val aggregate =
                        MetadataAggregate(
                            metadata = metadataRoot,
                            editions = listOf(EditionWithChapters(edition, chapters)),
                        )

                    val metadataId = metadataQueries.saveAggregate(aggregate)

                    val fetched = metadataQueries.getMetadataByBookId(bookId)
                    fetched.title shouldBe "Metadata Test Book"
                    fetched.description shouldBe "Test Description"
                    fetched.publisher shouldBe "Test Publisher"
                    fetched.published shouldBe 2024
                    fetched.genres.shouldContainExactlyInAnyOrder(listOf("Sci-Fi", "Adventure"))
                    fetched.moods.shouldContainExactlyInAnyOrder(listOf("Intriguing"))

                    val fetchedEditions = metadataQueries.getEditionsByBookId(bookId)
                    fetchedEditions.size shouldBe 1
                    val fetchedEdition = fetchedEditions.first()
                    fetchedEdition.format shouldBe BookFormat.EBOOK
                    fetchedEdition.path shouldBe storagePath
                    fetchedEdition.isbn13?.value shouldBe "9780553293357"

                    val fetchedChapters =
                        metadataQueries.getChaptersByEditionId(fetchedEdition.id.id)
                    fetchedChapters.size shouldBe 1
                    fetchedChapters.first().title shouldBe "Chapter 1"
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "getMetadataForBooks should return a map for multiple book IDs" {
            testWithDeps { deps ->
                val metadataQueries = deps.database.metadataQueries
                val bookQueries = deps.database.bookQueries

                recover({
                    val bookId1 = bookQueries.insert("Book 1", null).executeAsOne()
                    val bookId2 = bookQueries.insert("Book 2", null).executeAsOne()

                    metadataQueries.saveAggregate(
                        MetadataAggregate(
                            metadata =
                                MetadataRoot<PersistenceState.Unsaved>(
                                    id = Identity.Unsaved,
                                    bookId = bookId1,
                                    title = "Book 1",
                                ),
                            editions = emptyList(),
                        )
                    )

                    metadataQueries.saveAggregate(
                        MetadataAggregate(
                            metadata =
                                MetadataRoot<PersistenceState.Unsaved>(
                                    id = Identity.Unsaved,
                                    bookId = bookId2,
                                    title = "Book 2",
                                ),
                            editions = emptyList(),
                        )
                    )

                    val map = metadataQueries.getMetadataForBooks(listOf(bookId1, bookId2))
                    map.size shouldBe 2
                    map[bookId1]?.metadata?.title shouldBe "Book 1"
                    map[bookId2]?.metadata?.title shouldBe "Book 2"
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }

        "deleteMetadataByBookId should remove all associated metadata" {
            testWithDeps { deps ->
                val metadataQueries = deps.database.metadataQueries
                val bookQueries = deps.database.bookQueries

                recover({
                    val bookId = bookQueries.insert("To Delete", null).executeAsOne()
                    metadataQueries.saveAggregate(
                        MetadataAggregate(
                            metadata =
                                MetadataRoot<PersistenceState.Unsaved>(
                                    id = Identity.Unsaved,
                                    bookId = bookId,
                                    title = "To Delete",
                                ),
                            editions =
                                listOf(
                                    EditionWithChapters(
                                        edition =
                                            Edition(
                                                id = Identity.Unsaved,
                                                bookId = bookId,
                                                format = BookFormat.EBOOK,
                                                path = StoragePath.fromRaw("path"),
                                                size = 100,
                                            )
                                    )
                                ),
                        )
                    )

                    metadataQueries.deleteMetadataByBookId(bookId)

                    recover({
                        metadataQueries.getMetadataByBookId(bookId)
                        fail("Metadata should have been deleted")
                    }) {
                        it shouldBe MetadataNotFound
                    }
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
